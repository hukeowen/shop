#!/usr/bin/env bash
# Run V027 SQL via JDBC (no mysql client needed).
set -euo pipefail
cd "$(dirname "$0")/.."

JAR=$(find ~/.m2/repository -name "mysql-connector-j-*.jar" 2>/dev/null | head -1)
if [[ -z "$JAR" ]]; then
  echo "mysql-connector-j not found in ~/.m2"; exit 1
fi
echo "driver: $JAR"

cat > /tmp/RunSql.java <<'EOF'
import java.sql.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
public class RunSql {
  public static void main(String[] a) throws Exception {
    String url = "jdbc:mysql://47.109.143.146:3306/ruoyi-vue-pro?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";
    String user = "root", pass = "CHANGE_ME_5201314jxs@qq.com";
    String sqlText = new String(Files.readAllBytes(Paths.get(a[0])), StandardCharsets.UTF_8);
    String[] stmts = sqlText.split(";");
    Class.forName("com.mysql.cj.jdbc.Driver");
    try (Connection c = DriverManager.getConnection(url, user, pass);
         Statement s = c.createStatement()) {
      int i = 0;
      for (String stmt : stmts) {
        String t = stmt.replaceAll("(?m)^--.*$", "").trim();
        if (t.isEmpty()) continue;
        i++;
        String preview = t.substring(0, Math.min(80, t.length())).replace('\n', ' ');
        System.out.println("[" + i + "] " + preview);
        try {
          s.execute(t);
          System.out.println("    OK");
        } catch (SQLException e) {
          String msg = e.getMessage();
          if (msg.contains("Duplicate column") || msg.contains("already exists") || msg.contains("Duplicate key name")) {
            System.out.println("    SKIP (idempotent): " + msg);
          } else {
            System.out.println("    FAIL: " + msg);
          }
        }
      }
    }
  }
}
EOF

# Compile UTF-8 explicitly
javac -encoding UTF-8 /tmp/RunSql.java -d /tmp
java -cp "$JAR;/tmp" RunSql "$1"
