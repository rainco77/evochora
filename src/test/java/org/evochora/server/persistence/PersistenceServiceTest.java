package org.evochora.server.persistence;

import org.evochora.server.contracts.CellState;
import org.evochora.server.contracts.OrganismState;
import org.evochora.server.contracts.WorldStateMessage;
import org.evochora.server.queue.InMemoryTickQueue;
import org.evochora.server.queue.ITickMessageQueue;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceServiceTest {

    @Test
    void writesWorldStateRows() throws Exception {
        ITickMessageQueue q = new InMemoryTickQueue();
        PersistenceService persist = new PersistenceService(q);
        persist.start();

        // KORREKTUR: Der Konstruktoraufruf wurde an die neue Signatur angepasst.
        var org = new OrganismState(
                1, "progA", null, 0L, 123L,
                List.of(1,2), List.of(0,1), List.of(1,0),
                5, 0,
                List.of("DATA:1", "DATA:2"), // dataRegisters als List<String>
                List.of("DATA:3"),            // procRegisters als List<String>
                List.of("DATA:9", "DATA:8"),  // dataStack als List<String>
                List.of("MY_PROC"),           // callStack als List<String>
                List.of(),                    // NEU: formalParameters als leere Liste
                "{}"                          // disassembledInstructionJson
        );
        var cell = new CellState(List.of(1,2), 2, 42, 1);
        var wsm = new WorldStateMessage(10L, 999L, List.of(org), List.of(cell));

        q.put(wsm);

        // Give consumer a moment
        Thread.sleep(100);

        var db = persist.getDbFilePath();
        assertThat(Files.exists(db)).isTrue();

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath())) {
            ResultSet rsTick = c.createStatement().executeQuery("select count(*) from ticks where tickNumber=10");
            assertThat(rsTick.next()).isTrue();
            assertThat(rsTick.getInt(1)).isEqualTo(1);

            ResultSet rsOrg = c.createStatement().executeQuery("select count(*) from organism_states where tickNumber=10 and organismId=1 and programId='progA'");
            assertThat(rsOrg.next()).isTrue();
            assertThat(rsOrg.getInt(1)).isEqualTo(1);

            ResultSet rsCell = c.createStatement().executeQuery("select count(*) from cell_states where tickNumber=10");
            assertThat(rsCell.next()).isTrue();
            assertThat(rsCell.getInt(1)).isEqualTo(1);
        }

        persist.shutdown();
    }
}