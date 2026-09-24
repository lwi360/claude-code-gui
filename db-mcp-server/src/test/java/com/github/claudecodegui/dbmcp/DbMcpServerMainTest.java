package com.github.claudecodegui.dbmcp;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DbMcpServerMainTest {

    @Test
    public void parseIncludesPingFlag() {
        DbMcpServerMain.CliArguments arguments = DbMcpServerMain.CliArguments.parse(new String[] {
                "--config",
                "db.json",
                "--source",
                "main",
                "--ping"
        });

        assertEquals("main", arguments.sourceId());
        assertTrue(arguments.ping());
        assertEquals("db.json", arguments.configPath().toString());
    }

    @Test
    public void parseDefaultsPingToFalse() {
        DbMcpServerMain.CliArguments arguments = DbMcpServerMain.CliArguments.parse(new String[] {
                "--config",
                "db.json"
        });

        assertFalse(arguments.ping());
    }
}
