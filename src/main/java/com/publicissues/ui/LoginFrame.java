package com.publicissues.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import com.publicissues.model.User;
import com.publicissues.service.AuthService;
import com.publicissues.service.SessionManager;

public class LoginFrame extends JFrame {

    private final AuthService authService;
    private final Consumer<User> onAuthenticated;

    private final JTextField emailField;
    private final JPasswordField passwordField;
    private final JComboBox<String> roleBox;
    private final JButton loginButton;
    private final JButton registerButton;
    private boolean authenticationRunning;

    public LoginFrame(Consumer<User> onAuthenticated) {

        if (onAuthenticated == null) {
            throw new IllegalArgumentException(
                    "Authentication callback is required.");
        }

        this.authService = new AuthService();
        this.onAuthenticated = onAuthenticated;

        setTitle("Public Issue Reporting System - Login");
        setSize(420, 280);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (!authenticationRunning) {
                    dispose();
                }
            }
        });
        setLocationRelativeTo(null);
        setResizable(false);

        emailField = new JTextField(20);
        passwordField = new JPasswordField(20);

        roleBox = new JComboBox<>(
                new String[]{"Citizen", "Administrator"});

        loginButton = new JButton("Login");
        registerButton = new JButton("Register as Citizen");

        buildInterface();

        loginButton.addActionListener(e -> authenticate());
        registerButton.addActionListener(e -> openRegistration());

        getRootPane().setDefaultButton(loginButton);
    }

    private void buildInterface() {

        JPanel mainPanel = new JPanel(new BorderLayout(10, 15));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(
                20, 30, 20, 30));

        JLabel heading = new JLabel(
                "PUBLIC ISSUE REPORTING SYSTEM",
                JLabel.CENTER);

        JPanel formPanel = new JPanel(new GridLayout(3, 2, 10, 12));

        formPanel.add(new JLabel("Email:"));
        formPanel.add(emailField);

        formPanel.add(new JLabel("Password:"));
        formPanel.add(passwordField);

        formPanel.add(new JLabel("Login as:"));
        formPanel.add(roleBox);

        JPanel buttonPanel = new JPanel(new FlowLayout(
                FlowLayout.CENTER));

        buttonPanel.add(loginButton);
        buttonPanel.add(registerButton);

        mainPanel.add(heading, BorderLayout.NORTH);
        mainPanel.add(formPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private void authenticate() {

        String email = emailField.getText().trim();

        char[] passwordChars = passwordField.getPassword();

        final String password;
        try {
            if (email.isBlank() || passwordChars.length == 0) {
                JOptionPane.showMessageDialog(
                        this,
                        "Please enter your email and password.",
                        "Missing Details",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            password = new String(passwordChars);
        } finally {
            Arrays.fill(passwordChars, '\0');
        }

        String selectedRole = (String) roleBox.getSelectedItem();

        authenticationRunning = true;
        loginButton.setEnabled(false);
        registerButton.setEnabled(false);

        SwingWorker<User, Void> worker = new SwingWorker<>() {

            @Override
            protected User doInBackground() {

                if ("Administrator".equals(selectedRole)) {
                    return authService.loginAsAdmin(email, password);
                }

                return authService.loginAsCitizen(email, password);
            }

            @Override
            protected void done() {

                try {
                    User user = get();

                    try {
                        SessionManager.startSession(user);
                    } catch (IllegalArgumentException e) {
                        showError("Unable to start your session.");
                        return;
                    } catch (RuntimeException e) {
                        showError(
                                "Unable to complete sign in. Please try again.");
                        return;
                    }

                    passwordField.setText("");

                    dispose();

                    onAuthenticated.accept(user);

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();

                    showError("Login was interrupted.");

                } catch (ExecutionException e) {

                    Throwable cause = e.getCause();

                    if (cause instanceof IllegalArgumentException) {
                        showError(cause.getMessage());
                    } else {
                        showError(
                                "Unable to sign in. Please try again later.");
                    }

                } finally {

                    authenticationRunning = false;
                    if (isDisplayable()) {
                        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                        loginButton.setEnabled(true);
                        registerButton.setEnabled(true);
                    }
                }
            }
        };

        worker.execute();
    }

    private void openRegistration() {
        if (!loginButton.isEnabled()) {
            return;
        }
        new RegistrationFrame(onAuthenticated).setVisible(true);
        dispose();
    }

    private void showError(String message) {

        JOptionPane.showMessageDialog(
                this,
                message,
                "Login Failed",
                JOptionPane.ERROR_MESSAGE);
    }
}