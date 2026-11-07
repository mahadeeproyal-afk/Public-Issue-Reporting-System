package com.publicissues.dao;

import com.publicissues.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DashboardDAO {

    // Each row: 0 tracking, 1 title, 2 category, 3 location, 4 department,
    //           5 status, 6 date, 7 citizen name, 8 phone, 9 description
    public List<String[]> getAllIssueRows() throws SQLException {
        String sql =
            "SELECT i.tracking_code, i.title, i.category, i.location, "
          + "COALESCE(d.department_name, '-') AS department, "
          + "i.status, DATE(i.created_at) AS created, "
          + "u.name, u.phone, i.description "
          + "FROM issues i "
          + "LEFT JOIN app_users u ON i.citizen_id = u.user_id "
          + "LEFT JOIN departments d ON i.department_id = d.department_id "
          + "ORDER BY i.created_at DESC, i.issue_id DESC";

        List<String[]> rows = new ArrayList<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new String[] {
                    nz(rs.getString("tracking_code")),
                    nz(rs.getString("title")),
                    nz(rs.getString("category")),
                    nz(rs.getString("location")),
                    nz(rs.getString("department")),
                    nz(rs.getString("status")),
                    nz(rs.getString("created")),
                    nz(rs.getString("name")),
                    nz(rs.getString("phone")),
                    nz(rs.getString("description"))
                });
            }
        }
        return rows;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
