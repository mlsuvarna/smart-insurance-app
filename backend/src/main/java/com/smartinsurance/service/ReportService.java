package com.smartinsurance.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
    private final NamedParameterJdbcTemplate jdbc;
    public ReportService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> summary(LocalDate from, LocalDate to, Long productId, String claimType, String claimStatus) {
        validate(from, to);
        if (claimType != null && (claimType.isBlank() || claimType.length() > 80)) {
            throw new com.smartinsurance.exception.BusinessRuleException("Claim type filter is invalid");
        }
        if (claimStatus != null) {
            try { com.smartinsurance.enums.ClaimStatus.valueOf(claimStatus); }
            catch (IllegalArgumentException ex) {
                throw new com.smartinsurance.exception.BusinessRuleException("Claim status filter is invalid");
            }
        }
        MapSqlParameterSource params = params(from, to, productId)
                .addValue("claimType", claimType, java.sql.Types.VARCHAR)
                .addValue("claimStatus", claimStatus, java.sql.Types.VARCHAR);
        String where = "c.created_at >= :fromDate AND c.created_at < :toDate"
                + " AND (:productId IS NULL OR p.product_id = :productId)"
                + " AND (:claimType IS NULL OR c.claim_type = :claimType)"
                + " AND (:claimStatus IS NULL OR c.status = :claimStatus)";
        String sql = """
                SELECT COUNT(*) AS claim_volume,
                       COALESCE(SUM(c.status IN ('SUBMITTED','UNDER_REVIEW','INFO_REQUESTED')), 0) AS pending_count,
                       COALESCE(SUM(c.status = 'APPROVED'), 0) AS approved_count,
                       COALESCE(SUM(c.status = 'REJECTED'), 0) AS rejected_count,
                       COALESCE(SUM(c.status = 'SETTLED'), 0) AS settled_count,
                       COALESCE(SUM(c.settlement_amount), 0) AS settlement_total,
                       COALESCE(SUM(c.status = 'SETTLED') / NULLIF(SUM(c.status IN ('SETTLED','REJECTED')), 0), 0) AS settlement_ratio,
                       COALESCE(SUM(c.status = 'APPROVED') / NULLIF(COUNT(*), 0), 0) AS approval_rate,
                       COALESCE(SUM(c.status = 'REJECTED') / NULLIF(COUNT(*), 0), 0) AS rejection_rate,
                       COALESCE(AVG(CASE WHEN c.status IN ('SETTLED','REJECTED','WITHDRAWN')
                           THEN TIMESTAMPDIFF(SECOND, c.created_at, c.updated_at) END), 0) AS avg_processing_seconds
                FROM claims c JOIN policies p ON p.id = c.policy_id
                WHERE """ + where;
        return jdbc.queryForMap(sql, params);
    }

    public List<Map<String, Object>> topPremiumCustomers(LocalDate from, LocalDate to, Long productId) {
        validate(from, to);
        return jdbc.queryForList("""
                SELECT u.id AS customer_id, u.full_name, SUM(pay.amount) AS premium_total
                FROM users u JOIN payments pay ON pay.customer_id = u.id
                JOIN policies pol ON pol.id = pay.policy_id
                WHERE pay.status = 'SUCCESS' AND pay.created_at >= :fromDate AND pay.created_at < :toDate
                  AND (:productId IS NULL OR pol.product_id = :productId)
                GROUP BY u.id, u.full_name ORDER BY premium_total DESC LIMIT 10
                """, params(from, to, productId));
    }

    public List<Map<String, Object>> monthlyRevenue(LocalDate from, LocalDate to, Long productId) {
        validate(from, to);
        String productFilter = productId == null ? "" : " AND pol.product_id = :productId";
        return jdbc.queryForList("""
                SELECT DATE_FORMAT(pay.created_at, '%Y-%m') AS revenue_month, SUM(pay.amount) AS premium_revenue
                FROM payments pay JOIN policies pol ON pol.id = pay.policy_id
                WHERE pay.status = 'SUCCESS' AND pay.created_at >= :fromDate AND pay.created_at < :toDate
                """ + productFilter + " GROUP BY DATE_FORMAT(pay.created_at, '%Y-%m') ORDER BY revenue_month",
                params(from, to, productId));
    }

    public List<Map<String, Object>> productStatistics(LocalDate from, LocalDate to, Long productId,
                                                       String claimType, String claimStatus) {
        validate(from, to);
        validateClaimFilters(claimType, claimStatus);
        MapSqlParameterSource params = params(from, to, productId)
                .addValue("claimType", claimType, java.sql.Types.VARCHAR)
                .addValue("claimStatus", claimStatus, java.sql.Types.VARCHAR);
        return jdbc.queryForList("""
                SELECT product.id AS product_id, product.name,
                       COALESCE(claim_totals.claim_count, 0) AS claim_count,
                       COALESCE(premium_totals.premium_collected, 0) AS premium_collected,
                       COALESCE(claim_totals.settlements, 0) AS settlements,
                       COALESCE(premium_totals.premium_collected, 0) - COALESCE(claim_totals.settlements, 0) AS approximate_margin
                FROM insurance_products product
                LEFT JOIN (
                    SELECT pol.product_id, COUNT(c.id) AS claim_count, SUM(c.settlement_amount) AS settlements
                    FROM policies pol JOIN claims c ON c.policy_id = pol.id
                    WHERE c.created_at >= :fromDate AND c.created_at < :toDate
                      AND (:claimType IS NULL OR c.claim_type = :claimType)
                      AND (:claimStatus IS NULL OR c.status = :claimStatus)
                    GROUP BY pol.product_id
                ) claim_totals ON claim_totals.product_id = product.id
                LEFT JOIN (
                    SELECT pol.product_id, SUM(pay.amount) AS premium_collected
                    FROM policies pol JOIN payments pay ON pay.policy_id = pol.id
                    WHERE pay.status = 'SUCCESS' AND pay.created_at >= :fromDate AND pay.created_at < :toDate
                    GROUP BY pol.product_id
                ) premium_totals ON premium_totals.product_id = product.id
                WHERE (:productId IS NULL OR product.id = :productId)
                ORDER BY product.name
                """, params);
    }

    private MapSqlParameterSource params(LocalDate from, LocalDate to, Long productId) {
        return new MapSqlParameterSource().addValue("fromDate", from.atStartOfDay())
                .addValue("toDate", to.plusDays(1).atStartOfDay())
                .addValue("productId", productId, java.sql.Types.BIGINT);
    }

    private void validate(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to) || to.isAfter(LocalDate.now()) || from.isBefore(to.minusYears(5))) {
            throw new com.smartinsurance.exception.BusinessRuleException("Report dates must be a valid range within the last five years");
        }
    }

    private void validateClaimFilters(String claimType, String claimStatus) {
        if (claimType != null && (claimType.isBlank() || claimType.length() > 80)) {
            throw new com.smartinsurance.exception.BusinessRuleException("Claim type filter is invalid");
        }
        if (claimStatus != null) {
            try { com.smartinsurance.enums.ClaimStatus.valueOf(claimStatus); }
            catch (IllegalArgumentException ex) {
                throw new com.smartinsurance.exception.BusinessRuleException("Claim status filter is invalid");
            }
        }
    }
}
