package com.publicissues.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Insets;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import com.publicissues.model.User;
import com.publicissues.service.AuthService;

public class RegistrationFrame extends JFrame {

    private final AuthService authService;
    private final Consumer<User> onAuthenticated;

    private final JTextField nameField;
    private final JTextField emailField;
    private final JTextField phoneField;
    private final JPasswordField passwordField;
    private final JPasswordField confirmPasswordField;
    private final JButton registerButton;
    private final JButton backButton;
    private boolean registrationRunning;

    public RegistrationFrame(Consumer<User> onAuthenticated) {
        if (onAuthenticated == null) {
            throw new IllegalArgumentException(
                    "Authentication callback is required.");
        }

        this.authService = new AuthService();
        this.onAuthenticated = onAuthenticated;

        setTitle("Public Issue Reporting System - Registration");
        Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(560, screen.width - 40), Math.min(700, screen.height - 60));
        setMinimumSize(new Dimension(420, Math.min(500, screen.height - 40)));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (!registrationRunning) {
                    dispose();
                }
            }
        });
        setLocationRelativeTo(null);
        setResizable(true);

        nameField = new JTextField(20);
        emailField = new JTextField(20);
        phoneField = new JTextField(20);
        passwordField = new JPasswordField(20);
        confirmPasswordField = new JPasswordField(20);
        CivicTheme.styleField(nameField);
        CivicTheme.styleField(emailField);
        CivicTheme.styleField(phoneField);
        CivicTheme.styleField(passwordField);
        CivicTheme.styleField(confirmPasswordField);
        registerButton = CivicTheme.primaryButton("Create account");
        backButton = CivicTheme.secondaryButton("Back to sign in");

        buildInterface();

        registerButton.addActionListener(e -> register());
        backButton.addActionListener(e -> openLogin());
        getRootPane().setDefaultButton(registerButton);
    }

    private void buildInterface() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(CivicTheme.CANVAS);
        page.setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));
        JPanel card = new JPanel(new BorderLayout(0, 18));
        card.setBackground(CivicTheme.SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CivicTheme.BORDER),
                BorderFactory.createEmptyBorder(24, 32, 24, 32)));

        JPanel header = new JPanel(new GridBagLayout());
        header.setBackground(CivicTheme.SURFACE);
        GridBagConstraints h = new GridBagConstraints();
        h.gridx = 0; h.gridy = 0; h.anchor = GridBagConstraints.WEST;
        h.insets = new Insets(0, 0, 7, 0);
        JLabel eyebrow = new JLabel("CIVIC SERVICES  /  CITIZEN ACCESS");
        eyebrow.setFont(CivicTheme.BODY_BOLD);
        eyebrow.setForeground(CivicTheme.ACCENT);
        header.add(eyebrow, h);
        h.gridy++; h.insets = new Insets(0, 0, 6, 0);
        JLabel heading = new JLabel("Create your account");
        heading.setFont(CivicTheme.TITLE);
        header.add(heading, h);
        h.gridy++; h.insets = new Insets(0, 0, 0, 0);
        JLabel subtitle = new JLabel("Register as a citizen to submit and follow reports.");
        subtitle.setForeground(CivicTheme.MUTED);
        header.add(subtitle, h);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(CivicTheme.SURFACE);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        addFormRow(form, c, "Full name *", nameField);
        addFormRow(form, c, "Email address *", emailField);
        addFormRow(form, c, "Phone (optional)", phoneField);
        addFormRow(form, c, "Password * (8 or more characters)", passwordField);
        addFormRow(form, c, "Confirm password *", confirmPasswordField);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setBackground(CivicTheme.SURFACE);
        actions.add(backButton);
        actions.add(registerButton);
        card.add(header, BorderLayout.NORTH);
        card.add(form, BorderLayout.CENTER);
        JScrollPane scroll = CivicTheme.scrollPane(card);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        page.add(scroll, BorderLayout.CENTER);
        page.add(actions, BorderLayout.SOUTH);
        add(page);
    }

    private void addFormRow(JPanel form, GridBagConstraints c,
                            String labelText, JTextField field) {
        c.gridx = 0; c.gridy++; c.weightx = 1;
        c.insets = new Insets(0, 0, 5, 0);
        form.add(new JLabel(labelText), c);
        c.gridy++; c.insets = new Insets(0, 0, 12, 0);
        form.add(field, c);
    }

    private void register() {
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String phone = phoneField.getText().trim();
        char[] passwordChars = passwordField.getPassword();
        char[] confirmationChars = confirmPasswordField.getPassword();

        final String password;
        try {
            if (name.isBlank() || email.isBlank()
                    || passwordChars.length == 0
                    || confirmationChars.length == 0) {
                showError("Please complete all required fields.");
                return;
            }

            if (!Arrays.equals(passwordChars, confirmationChars)) {
                showError("Passwords do not match.");
                return;
            }

            password = new String(passwordChars);
        } finally {
            clearPasswords(passwordChars, confirmationChars);
        }

        registerButton.setEnabled(false);
        backButton.setEnabled(false);
        registrationRunning = true;

        SwingWorker<User, Void> worker = new SwingWorker<>() {
            @Override
            protected User doInBackground() {
                return authService.registerCitizen(
                        name, email, phone, password);
            }

            @Override
            protected void done() {
                try {
                    get();
                    passwordField.setText("");
                    confirmPasswordField.setText("");
                    JOptionPane.showMessageDialog(
                            RegistrationFrame.this,
                            "Registration successful. Please log in.",
                            "Registration Complete",
                            JOptionPane.INFORMATION_MESSAGE);
                    registrationRunning = false;
                    openLogin();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError("Registration was interrupted.");
                } catch (ExecutionException e) {
                    showFailure(e.getCause(), "Unable to register.");
                } finally {
                    registrationRunning = false;
                    if (isDisplayable()) {
                        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                        registerButton.setEnabled(true);
                        backButton.setEnabled(true);
                    }
                }
            }
        };

        worker.execute();
    }

    private void openLogin() {
        if (registrationRunning) {
            return;
        }
        new LoginFrame(onAuthenticated).setVisible(true);
        dispose();
    }

    private void clearPasswords(char[] password, char[] confirmation) {
        Arrays.fill(password, '\0');
        Arrays.fill(confirmation, '\0');
    }

    private void showFailure(Throwable cause, String fallbackMessage) {
        if (cause instanceof IllegalArgumentException
                && cause.getMessage() != null) {
            showError(cause.getMessage());
        } else {
            showError(fallbackMessage + " Please try again later.");
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(
                this, message, "Registration Failed",
                JOptionPane.ERROR_MESSAGE);
    }
}
