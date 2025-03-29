package com.jetbrains.renamecommitplugin;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class EnterCommitMessageDialog extends DialogWrapper {
    private final JTextArea textArea = new JTextArea(5, 30);
    private final String message;

    public EnterCommitMessageDialog(String currentMessage, int width, int height) {
        super(true);
        this.message = currentMessage;
        setTitle("Rename Commit");
        setSize(width, height);
        init();
    }

    public EnterCommitMessageDialog(String currentMessage) {
        super(true);
        this.message = currentMessage;
        setTitle("Rename Commit");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(JBColor.PanelBackground);

        JLabel label = new JLabel("Enter new commit message:");
        panel.add(label, BorderLayout.NORTH);

        textArea.setText(message);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(UIManager.getFont("TextField.font"));

        JScrollPane scrollPane = new JBScrollPane(textArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    public String getCommitMessage() {
        return textArea.getText();
    }
}
