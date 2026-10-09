import org.jooq.impl.DSL;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

class PostSearch {

  JdbcTemplate jdbc;
  java.sql.Connection connection;

  void byTitle(@RequestParam String title) throws Exception {
    java.sql.Statement statement = connection.createStatement();
    // ruleid: sql-injection-request-input
    statement.executeQuery("select * from posts where title = '" + title + "'");
  }

  void byId(@PathVariable String id) {
    // ruleid: sql-injection-request-input
    jdbc.update("delete from posts where id = '" + id + "'");
  }

  void raw(@RequestBody String column) {
    // ruleid: sql-injection-request-input
    DSL.sql("order by " + column);
  }

  void viaHelper(@RequestParam String title) {
    deleteByTitle(title);
  }

  void deleteByTitle(String title) {
    // ruleid: sql-injection-request-input
    jdbc.update("delete from posts where title = '" + title + "'");
  }

  void byBoundId(@PathVariable String id) {
    // ok: sql-injection-request-input
    jdbc.update("delete from posts where id = ?", id);
  }

  void safeTitle(@RequestParam String title) throws Exception {
    java.sql.PreparedStatement statement = connection.prepareStatement("select * from posts where title = ?");
    statement.setString(1, title);
    // ok: sql-injection-request-input
    statement.executeQuery();
  }

  void constantQuery(@RequestParam String title) {
    // ok: sql-injection-request-input
    jdbc.update("delete from posts where archived = true");
  }
}
