package com.jetbrains.renamecommitplugin;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.vcs.log.VcsCommitMetadata;
import git4idea.GitLocalBranch;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class RenameCommitAction extends AnAction {

    private Project project;

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread(){
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);

        Presentation presentation = e.getPresentation();
        project = e.getProject();
        if(project == null){
            presentation.setEnabledAndVisible(false);
            presentation.setVisible(true);
            return;
        }

        // If there are no repositories, prevent action execution
        Collection<GitRepository> repositories = GitUtil.getRepositories(e.getProject());
        if (GitUtil.getRepositories(e.getProject()).isEmpty()) {
            presentation.setEnabledAndVisible(false);
            presentation.setVisible(true);
            return;
        }

        // If there are no commit, prevent action execution
        GitRepository repository = repositories.iterator().next();
        CompletableFuture<Void> result = new CompletableFuture<>();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try{
                getLastCommitMessage(repository);
                result.complete(null);
            }catch (Exception ex){
                presentation.setEnabledAndVisible(false);
                presentation.setVisible(true);
            }
        });

        result.join();
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        project = e.getProject();
        if (project == null) return;

        // Execute the logic in background thread
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Fetching last commit", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {

                // Get git repository object
                GitRepository repository = GitRepositoryManager.getInstance(project).getRepositories().stream().findFirst().orElse(null);
                if (repository == null) {
                    displayNotification("GitErrorNotification",
                            "Error fetching last commit",
                            "No repositories found");
                    return;
                }

                // Get last commit message, if none found display error notification
                String lastCommitMessage;
                try{
                    lastCommitMessage = getLastCommitMessage(repository);
                }catch(Exception ex){
                    displayNotification("GitErrorNotification",
                            "Error fetching last commit",
                            ex.getMessage());
                    return;
                }

                CompletableFuture<String> newCommitMessageFuture = new CompletableFuture<>();

                // Generate dialog on EDT
                ApplicationManager.getApplication().invokeLater(() -> {
                    newCommitMessageFuture.complete(requestNewCommitMessage(lastCommitMessage));
                });
                String newCommitMessage = newCommitMessageFuture.join();

                // Cancel button was pressed
                if(newCommitMessage == null){
                    return;
                }
                newCommitMessage = newCommitMessage.replace('\n', ' ');

                // User entered empty String
                if(newCommitMessage.isBlank()){
                    displayNotification("GitErrorNotification",
                            "Error changing commit message",
                            "Empty commit message not allowed.");
                    return;
                }

                // Valid message was entered, proceed to change the message of the last commit
                try{
                    amendCommitMessage(repository, newCommitMessage);
                }catch (Exception ex){
                    displayNotification("GitErrorNotification",
                            "Error changing commit message",
                            ex.getMessage());
                }
            }
        });
    }

    private void displayNotification(String groupId, String title, String message){
        Notification notification = new Notification(
                groupId,
                title,
                message,
                NotificationType.ERROR
        );
        Notifications.Bus.notify(notification, project);
    }

    private void amendCommitMessage(GitRepository repository, String newMessage) throws Exception {
        GitLineHandler handler = new GitLineHandler(project, repository.getRoot(), GitCommand.COMMIT);
        handler.addParameters("--amend", "-m", newMessage);

        GitCommandResult result = Git.getInstance().runCommand(handler);

        if(!result.success()){
            throw new Exception("There was a problem executing a git command.");
        }
    }


    private String requestNewCommitMessage(String lastCommitMessage){
        EnterCommitMessageDialog dialog = new EnterCommitMessageDialog(lastCommitMessage,400, 300);
        if (dialog.showAndGet()) {  // Wait for user input
            return dialog.getCommitMessage();
        }
        return null;
    }


    private VcsCommitMetadata getLastCommitFromRepository(Project project, GitRepository repository) throws VcsException {
        List<? extends VcsCommitMetadata> commits = GitHistoryUtils.collectCommitsMetadata(project, repository.getRoot(), "HEAD");
        return commits == null || commits.isEmpty() ? null : commits.get(0);
    }

    private String getLastCommitMessage(@NotNull GitRepository repository) throws Exception {
        GitLocalBranch currentBranch = repository.getCurrentBranch();
        if (currentBranch != null) {
            try {
                return Objects.requireNonNull(getLastCommitFromRepository(project, repository)).getFullMessage();
            } catch (VcsException e) {
                throw new Exception("There was a problem executing a git command.");
            }
            catch (NullPointerException e){
                throw new Exception("No commits found");
            }
        }
        return null;
    }
}
