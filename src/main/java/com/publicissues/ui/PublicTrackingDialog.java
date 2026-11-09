package com.publicissues.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import com.publicissues.model.PublicIssueTracking;
import com.publicissues.service.IssueService;

/** Unauthenticated public tracking view, restricted to the DTO allowlist. */
public final class PublicTrackingDialog extends JDialog {
    private final JTextField codeField = new JTextField(25);
    private final JButton searchButton = CivicTheme.primaryButton("Check status");
    private final JLabel message = new JLabel("Enter the tracking code from your submission receipt.");
    private final JPanel resultCard = new JPanel(new GridBagLayout());
    private final JLabel trackingValue = new JLabel("—");
    private final JLabel titleValue = new JLabel("—");
    private final JLabel categoryValue = new JLabel("—");
    private final JLabel statusValue = new JLabel("—");
    private final JLabel submittedValue = new JLabel("—");

    public PublicTrackingDialog(JFrame owner) {
        super(owner, "Public complaint tracking", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(660, screen.width - 36), Math.min(650, screen.height - 50));
        setMinimumSize(new Dimension(440, Math.min(420, screen.height - 30)));
        setLocationRelativeTo(owner);
        CivicTheme.styleField(codeField);
        buildInterface();
        searchButton.addActionListener(event -> search());
        getRootPane().setDefaultButton(searchButton);
    }

    private void buildInterface() {
        JPanel page = new JPanel(new BorderLayout(0, 18));
        page.setBackground(CivicTheme.CANVAS);
        page.setBorder(BorderFactory.createEmptyBorder(24, 26, 24, 26));

        JPanel card = new JPanel(new BorderLayout(0, 18));
        card.setBackground(CivicTheme.SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CivicTheme.BORDER),
                BorderFactory.createEmptyBorder(24, 28, 24, 28)));

        JPanel header = new JPanel(new GridBagLayout());
        header.setBackground(CivicTheme.SURFACE);
        GridBagConstraints h = new GridBagConstraints();
        h.gridx = 0; h.gridy = 0; h.anchor = GridBagConstraints.WEST;
        h.insets = new Insets(0, 0, 7, 0);
        JLabel eyebrow = new JLabel("PUBLIC ACCESS");
        eyebrow.setFont(CivicTheme.BODY_BOLD);
        eyebrow.setForeground(CivicTheme.ACCENT);
        header.add(eyebrow, h);
        h.gridy++; h.insets = new Insets(0, 0, 6, 0);
        JLabel title = new JLabel("Track a complaint");
        title.setFont(CivicTheme.TITLE);
        header.add(title, h);
        h.gridy++; h.insets = new Insets(0, 0, 0, 0);
        JLabel subtitle = new JLabel("A tracking code shows a limited public status summary.");
        subtitle.setForeground(CivicTheme.MUTED);
        header.add(subtitle, h);

        JPanel lookup = new JPanel(new BorderLayout(10, 0));
        lookup.setBackground(CivicTheme.SURFACE);
        lookup.add(codeField, BorderLayout.CENTER);
        lookup.add(searchButton, BorderLayout.EAST);

        resultCard.setBackground(CivicTheme.SURFACE);
        resultCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CivicTheme.BORDER),
                BorderFactory.createEmptyBorder(15, 18, 15, 18)));
        int row = 0;
        addResultRow("Tracking code", trackingValue, row++);
        addResultRow("Title", titleValue, row++);
        addResultRow("Category", categoryValue, row++);
        addResultRow("Current status", statusValue, row++);
        addResultRow("Submitted", submittedValue, row);
        resultCard.setVisible(false);

        JPanel body = new JPanel(new BorderLayout(0, 16));
        body.setBackground(CivicTheme.SURFACE);
        message.setForeground(CivicTheme.MUTED);
        JPanel results = new JPanel(new BorderLayout(0, 12));
        results.setBackground(CivicTheme.SURFACE);
        results.add(message, BorderLayout.NORTH);
        results.add(resultCard, BorderLayout.CENTER);
        body.add(lookup, BorderLayout.NORTH);
        body.add(results, BorderLayout.CENTER);
        card.add(header, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setBackground(CivicTheme.SURFACE);
        JButton close = CivicTheme.secondaryButton("Close");
        close.addActionListener(event -> dispose());
        footer.add(close);
        card.add(footer, BorderLayout.SOUTH);
        page.add(CivicTheme.scrollPane(card), BorderLayout.CENTER);
        setContentPane(page);
    }

    private void addResultRow(String label, JLabel value, int row) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.gridx = 0; c.weightx = 0; c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(7, 0, 7, 18);
        JLabel field = new JLabel(label);
        field.setForeground(CivicTheme.MUTED);
        resultCard.add(field, c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(7, 0, 7, 0);
        value.setFont(CivicTheme.BODY_BOLD);
        resultCard.add(value, c);
    }

    private void search() {
        String code = codeField.getText().trim();
        if (code.isEmpty()) {
            resultCard.setVisible(false);
            message.setText("Enter a tracking code to continue.");
            return;
        }
        searchButton.setEnabled(false);
        message.setForeground(CivicTheme.MUTED);
        message.setText("Checking the tracking code…");
        new SwingWorker<PublicIssueTracking, Void>() {
            @Override protected PublicIssueTracking doInBackground() {
                return new IssueService().trackPublicIssue(code);
            }

            @Override protected void done() {
                searchButton.setEnabled(true);
                if (!isDisplayable()) return;
                try {
                    PublicIssueTracking tracking = get();
                    if (tracking == null) {
                        resultCard.setVisible(false);
                        message.setText("No complaint was found for that tracking code.");
                        revalidate();
                        repaint();
                        return;
                    }
                    trackingValue.setText(tracking.trackingCode());
                    trackingValue.setToolTipText(tracking.trackingCode());
                    titleValue.setText(tracking.title());
                    categoryValue.setText(tracking.category());
                    statusValue.setText(tracking.status());
                    submittedValue.setText(tracking.submittedAt() == null
                            ? "Unavailable" : tracking.submittedAt().toString());
                    resultCard.setVisible(true);
                    message.setText("This is the public tracking summary. Private details are not shown.");
                    revalidate();
                    repaint();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    message.setText("Tracking was interrupted. Please try again.");
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    message.setForeground(new java.awt.Color(166, 60, 53));
                    message.setText(cause instanceof IllegalArgumentException
                            ? "Use the expected format: ISS- followed by 26 hexadecimal characters."
                            : "Unable to check the code right now. Please try again later.");
                    resultCard.setVisible(false);
                    revalidate();
                    repaint();
                }
            }
        }.execute();
    }
}
