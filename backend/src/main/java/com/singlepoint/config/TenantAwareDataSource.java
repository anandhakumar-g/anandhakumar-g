package com.singlepoint.config;

import com.singlepoint.security.TenantContext;
import com.singlepoint.security.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

/**
 * Wraps every borrowed {@link Connection} so the PostgreSQL session GUC
 * {@code app.current_tenant_id} reflects the current {@link TenantContext} — this is what
 * makes the RLS policies in V2 enforce tenant isolation. The GUC is reset when the
 * connection is returned to the pool.
 *
 * Value applied:
 *   - a tenant UUID   -> normal tenant-scoped request
 *   - {@code *}        -> Super Admin / system job (cross-tenant)
 *   - empty string    -> no tenant context: every RLS policy denies (fail closed)
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareDataSource.class);
    private static final Pattern UUID_RE =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return prepare(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return prepare(super.getConnection(username, password));
    }

    private Connection prepare(Connection real) throws SQLException {
        String raw = TenantContext.get();
        String value;
        if (TenantContext.WILDCARD.equals(raw)) {
            value = "*";
        } else if (raw != null && UUID_RE.matcher(raw).matches()) {
            value = raw;
        } else {
            value = "";
        }
        String user = UserContext.get();
        String userValue = (user != null && UUID_RE.matcher(user).matches()) ? user : "";
        try (Statement s = real.createStatement()) {
            s.execute("SET app.current_tenant_id = '" + value + "'");
            s.execute("SET app.current_user_id = '" + userValue + "'");
        }
        return (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                new ResettingHandler(real));
    }

    /** Resets the GUC on close() so a pooled connection never leaks tenant scope. */
    private static final class ResettingHandler implements InvocationHandler {
        private final Connection target;

        ResettingHandler(Connection target) { this.target = target; }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("close".equals(method.getName())) {
                if (!target.isClosed()) {
                    try (Statement s = target.createStatement()) {
                        s.execute("RESET app.current_tenant_id");
                        s.execute("RESET app.current_user_id");
                    } catch (Exception e) {
                        log.debug("failed to reset app GUCs on close: {}", e.toString());
                    }
                }
                return method.invoke(target, args);
            }
            if ("unwrap".equals(method.getName()) && args != null && args.length == 1
                    && ((Class<?>) args[0]).isInstance(target)) {
                return target;
            }
            return method.invoke(target, args);
        }
    }
}
