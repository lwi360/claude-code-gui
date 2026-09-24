package com.github.claudecodegui.dbmcp;

import com.github.claudecodegui.dbmcp.config.ConfigLoader;
import com.github.claudecodegui.dbmcp.config.ServerConfiguration;
import com.github.claudecodegui.dbmcp.jdbc.JdbcConnectionFactory;
import com.github.claudecodegui.dbmcp.jdbc.JdbcExecutionService;
import com.github.claudecodegui.dbmcp.mcp.DbMcpToolRegistry;
import com.google.gson.Gson;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DbMcpServerMain {

    private static final Logger LOG = LoggerFactory.getLogger(DbMcpServerMain.class);
    private static final Gson GSON = new Gson();
    private static final String SERVER_NAME = "db-mcp-server";
    private static final String SERVER_VERSION = "0.1.0-SNAPSHOT";

    private DbMcpServerMain() {
    }

    public static void main(String[] args) throws Exception {
        CliArguments cliArguments = CliArguments.parse(args);
        ServerConfiguration configuration = ConfigLoader.load(cliArguments.configPath(), cliArguments.sourceId());
        if (cliArguments.ping()) {
            pingAndExit(configuration);
            return;
        }

        StdioServerTransportProvider transportProvider =
                new StdioServerTransportProvider(new JacksonMcpJsonMapper(JsonMapper.builder().build()));

        DbMcpToolRegistry toolRegistry = new DbMcpToolRegistry(configuration);

        var server = McpServer.sync(transportProvider)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(ServerCapabilities.builder().tools(true).build())
                .tools(toolRegistry.tools())
                .build();

        LOG.info("db-mcp-server started with source {}", configuration.activeSource().id());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.closeGracefully();
            } catch (Exception e) {
                LOG.warn("Failed to close db-mcp-server cleanly", e);
            }
        }));
    }

    private static void pingAndExit(ServerConfiguration configuration) {
        Map<String, Object> payload = new LinkedHashMap<>();
        int exitCode = 1;
        try {
            payload.putAll(new JdbcExecutionService(new JdbcConnectionFactory()).ping(configuration.activeSource()));
            exitCode = Boolean.TRUE.equals(payload.get("ok")) ? 0 : 1;
        } catch (Exception e) {
            payload.put("ok", false);
            payload.put("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            LOG.warn("Database ping failed", e);
        }
        System.out.println(GSON.toJson(payload));
        System.exit(exitCode);
    }

    public record CliArguments(Path configPath, String sourceId, boolean ping) {
        static CliArguments parse(String[] args) {
            Path configPath = null;
            String sourceId = null;
            boolean ping = false;
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if ("--config".equals(arg) && i + 1 < args.length) {
                    configPath = Path.of(args[++i]);
                } else if ("--source".equals(arg) && i + 1 < args.length) {
                    sourceId = args[++i];
                } else if ("--ping".equals(arg)) {
                    ping = true;
                }
            }

            if (configPath == null) {
                throw new IllegalArgumentException("Missing required argument: --config <path>");
            }
            return new CliArguments(configPath, sourceId, ping);
        }
    }
}
