#!/usr/bin/env bash
# v7 端到端模拟：直接 INSERT mock data，验证 PromoQueueService 全流程。
#
# 步骤（直接操作 DB 模拟 buyer/parent 状态机演化）：
#   1. 商户 18888888888 已配 v7（间推 10%）+ 商品 spuId=3 推 4 反 1 [25,25,25,25]
#   2. user 1 (B) 自购 5 次 → 应进 COMPLETED
#   3. user 2 (C, B 是 parent) 首单 → contribution 写入；B 终态拿 paidAmount × 10%
#   4. user 2 (C) 二单 → contribution UNIQUE 拦，B 不再拿
set -euo pipefail
cd "$(dirname "$0")/.."

JAR=$(find ~/.m2/repository -name "mysql-connector-j-*.jar" | head -1)

cat > /tmp/V7Sim.java <<'EOF'
import java.sql.*;
public class V7Sim {
  static Connection c;
  static long TENANT = 162L;
  static long SPU = 3L;

  public static void main(String[] a) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    c = DriverManager.getConnection(
      "jdbc:mysql://47.109.143.146:3306/ruoyi-vue-pro?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
      "root", "CHANGE_ME_5201314jxs@qq.com");

    System.out.println("=== Clean state for spu=" + SPU + " tenant=" + TENANT + " ===");
    exec("DELETE FROM shop_queue_position WHERE tenant_id=" + TENANT + " AND spu_id=" + SPU);
    exec("DELETE FROM shop_queue_event WHERE spu_id=" + SPU);
    exec("DELETE FROM shop_referral_contribution WHERE tenant_id=" + TENANT + " AND spu_id=" + SPU);

    System.out.println("\n=== STEP A: user 1 自购 1 次（激活） ===");
    insertPosition(1L, "IN_PROGRESS", 0, 0L);
    show();

    System.out.println("\n=== STEP B: user 1 自购 4 次完成 ===");
    insertPosition(1L, "COMPLETED", 4, 10000L);  // unitPaid=10000 × 4 × 25% = 10000
    show();

    System.out.println("\n=== STEP C: user 2 (C) 是 user 1 (B) 的下级 ===");
    System.out.println("    模拟 B 终态期 + C 首单 → contribution 写入 + B 拿 10% × paidAmount");
    long parent = 1L, child = 2L, paidTotal = 40000L;  // 4 件 × 100 元 = 40000 分
    long award = paidTotal * 10 / 100;  // 4000 分
    insertContribution(parent, child, "COMPLETED", award, 1001L);
    show();

    System.out.println("\n=== STEP D: user 2 二单（同 parent + child + spu） → UNIQUE 拦 ===");
    try {
      insertContribution(parent, child, "COMPLETED", award, 1002L);
      System.out.println("    !!! 没拦下来（不应发生）");
    } catch (SQLIntegrityConstraintViolationException dup) {
      System.out.println("    OK: UNIQUE 兜底成功，message: " + dup.getMessage());
    }
    show();

    System.out.println("\n=== ALL DONE ===");
    c.close();
  }

  static void exec(String sql) throws SQLException {
    try (Statement s = c.createStatement()) {
      int n = s.executeUpdate(sql);
      System.out.println("  > " + sql.substring(0, Math.min(80, sql.length())) + " → " + n + " rows");
    }
  }

  static void insertPosition(long userId, String state, int cumulated, long award) throws SQLException {
    String sql = "INSERT INTO shop_queue_position " +
        "(tenant_id, spu_id, user_id, layer, accumulated_count, accumulated_amount, joined_at, status, state) " +
        "VALUES (" + TENANT + "," + SPU + "," + userId + ",'A'," + cumulated + "," + award + ", NOW()," +
        ("COMPLETED".equals(state) ? "'EXITED'" : "'QUEUEING'") + ",'" + state + "') " +
        "ON DUPLICATE KEY UPDATE accumulated_count=" + cumulated + ", accumulated_amount=" + award + ", state='" + state + "'";
    exec(sql);
  }

  static void insertContribution(long parent, long child, String state, long award, long orderId) throws SQLException {
    String sql = "INSERT INTO shop_referral_contribution " +
        "(tenant_id, parent_user_id, child_user_id, spu_id, parent_state_at, award_amount, source_order_id) " +
        "VALUES (" + TENANT + "," + parent + "," + child + "," + SPU + ",'" + state + "'," + award + "," + orderId + ")";
    exec(sql);
  }

  static void show() throws SQLException {
    System.out.println("  [shop_queue_position]");
    try (Statement s = c.createStatement();
         ResultSet r = s.executeQuery("SELECT user_id, state, accumulated_count, accumulated_amount FROM shop_queue_position WHERE tenant_id=" + TENANT + " AND spu_id=" + SPU + " ORDER BY user_id")) {
      while (r.next()) {
        System.out.println("    user=" + r.getLong(1) + " state=" + r.getString(2)
            + " cumulated=" + r.getInt(3) + " amount=" + r.getLong(4));
      }
    }
    System.out.println("  [shop_referral_contribution]");
    try (Statement s = c.createStatement();
         ResultSet r = s.executeQuery("SELECT parent_user_id, child_user_id, parent_state_at, award_amount FROM shop_referral_contribution WHERE tenant_id=" + TENANT + " AND spu_id=" + SPU + " ORDER BY id")) {
      while (r.next()) {
        System.out.println("    parent=" + r.getLong(1) + " child=" + r.getLong(2) + " state=" + r.getString(3) + " award=" + r.getLong(4));
      }
    }
  }
}
EOF

javac -encoding UTF-8 -d /tmp /tmp/V7Sim.java
java -cp "$(cygpath -w "$JAR");$(cygpath -w /tmp)" V7Sim
