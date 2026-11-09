package com.publicissues.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

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
    private final JButton trackButton;
    private boolean authenticationRunning;

    public LoginFrame(Consumer<User> onAuthenticated) {

        if (onAuthenticated == null) {
            throw new IllegalArgumentException(
                    "Authentication callback is required.");
        }

        this.authService = new AuthService();
        this.onAuthenticated = onAuthenticated;

        setTitle("Public Issue Reporting System - Login");
        setSize(500, 560);
        setMinimumSize(new Dimension(460, 520));
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
        setResizable(true);

        emailField = new JTextField(20);
        passwordField = new JPasswordField(20);

        roleBox = new JComboBox<>(
                new String[]{"Citizen", "Administrator"});

        CivicTheme.styleField(emailField);
        CivicTheme.styleField(passwordField);
        loginButton = CivicTheme.primaryButton("Sign in");
        registerButton = CivicTheme.secondaryButton("Create citizen account");
        trackButton = CivicTheme.secondaryButton("Track a complaint");
        CivicTheme.styleComboBox(roleBox);
        roleBox.setBackground(CivicTheme.SURFACE);

        buildInterface();

        loginButton.addActionListener(e -> authenticate());
        registerButton.addActionListener(e -> openRegistration());
        trackButton.addActionListener(e -> openPublicTracking());

        getRootPane().setDefaultButton(loginButton);
    }

    private void buildInterface() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(CivicTheme.CANVAS);
        page.setBorder(javax.swing.BorderFactory.createEmptyBorder(24, 26, 24, 26));

        JPanel card = new JPanel(new BorderLayout(0, 22));
        card.setBackground(CivicTheme.SURFACE);
        card.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(CivicTheme.BORDER),
                javax.swing.BorderFactory.createEmptyBorder(28, 34, 25, 34)));

        JLabel eyebrow = new JLabel("CIVIC SERVICES  /  PUBLIC REPORTING");
        eyebrow.setFont(CivicTheme.BODY_BOLD);
        eyebrow.setForeground(CivicTheme.ACCENT);
        JLabel heading = new JLabel("Welcome back");
        heading.setFont(CivicTheme.TITLE);
        JLabel subtitle = new JLabel("Sign in to follow and manage community issues.");
        subtitle.setForeground(CivicTheme.MUTED);
        JPanel headingPanel = new JPanel(new GridBagLayout());
        headingPanel.setBackground(CivicTheme.SURFACE);
        GridBagConstraints h = new GridBagConstraints();
        h.gridx = 0; h.gridy = 0; h.anchor = GridBagConstraints.WEST;
        h.insets = new Insets(0, 0, 8, 0); headingPanel.add(eyebrow, h);
        h.gridy++; h.insets = new Insets(0, 0, 6, 0); headingPanel.add(heading, h);
        h.gridy++; h.insets = new Insets(0, 0, 0, 0); headingPanel.add(subtitle, h);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(CivicTheme.SURFACE);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST; c.insets = new Insets(0, 0, 6, 0);
        form.add(new JLabel("Email address"), c);
        c.gridy++; c.insets = new Insets(0, 0, 16, 0); form.add(emailField, c);
        c.gridy++; c.insets = new Insets(0, 0, 6, 0); form.add(new JLabel("Password"), c);
        c.gridy++; c.insets = new Insets(0, 0, 16, 0); form.add(passwordField, c);
        c.gridy++; c.insets = new Insets(0, 0, 6, 0); form.add(new JLabel("Sign in as"), c);
        c.gridy++; c.insets = new Insets(0, 0, 0, 0); form.add(roleBox, c);

        JPanel footer = new JPanel(new GridBagLayout());
        footer.setBackground(CivicTheme.SURFACE);
        GridBagConstraints f = new GridBagConstraints();
        f.gridx = 0; f.gridy = 0; f.weightx = 1; f.fill = GridBagConstraints.HORIZONTAL;
        f.insets = new Insets(0, 0, 12, 0); footer.add(loginButton, f);
        JPanel links = new JPanel(new FlowLayout(FlowLayout.CENTER, 7, 0));
        links.setBackground(CivicTheme.SURFACE);
        links.add(registerButton); links.add(trackButton);
        f.gridy++; f.insets = new Insets(0, 0, 0, 0); footer.add(links, f);

        card.add(headingPanel, BorderLayout.NORTH);
        card.add(form, BorderLayout.CENTER);
        card.add(footer, BorderLayout.SOUTH);
        page.add(card, BorderLayout.CENTER);
        add(CivicTheme.scrollPane(page));
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

    private void openPublicTracking() {
        if (!authenticationRunning) new PublicTrackingDialog(this).setVisible(true);
    }

    private void showError(String message) {

        JOptionPane.showMessageDialog(
                this,
                message,
                "Login Failed",
                JOptionPane.ERROR_MESSAGE);
    }
}
