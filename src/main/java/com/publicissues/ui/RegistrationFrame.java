package com.publicissues.ui;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.GridLayout;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
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
        setSize(480, 360);
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
        setResizable(false);

        nameField = new JTextField(20);
        emailField = new JTextField(20);
        phoneField = new JTextField(20);
        passwordField = new JPasswordField(20);
        confirmPasswordField = new JPasswordField(20);
        registerButton = new JButton("Register");
        backButton = new JButton("Back to Login");

        buildInterface();

        registerButton.addActionListener(e -> register());
        backButton.addActionListener(e -> openLogin());
        getRootPane().setDefaultButton(registerButton);
    }

    private void buildInterface() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 15));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(
                20, 30, 20, 30));

        JLabel heading = new JLabel(
                "CREATE CITIZEN ACCOUNT", JLabel.CENTER);

        JPanel formPanel = new JPanel(new GridLayout(5, 2, 10, 12));
        formPanel.add(new JLabel("Full name:"));
        formPanel.add(nameField);
        formPanel.add(new JLabel("Email:"));
        formPanel.add(emailField);
        formPanel.add(new JLabel("Phone (optional):"));
        formPanel.add(phoneField);
        formPanel.add(new JLabel("Password:"));
        formPanel.add(passwordField);
        formPanel.add(new JLabel("Confirm password:"));
        formPanel.add(confirmPasswordField);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(registerButton);
        buttonPanel.add(backButton);

        mainPanel.add(heading, BorderLayout.NORTH);
        mainPanel.add(formPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        add(mainPanel);
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
