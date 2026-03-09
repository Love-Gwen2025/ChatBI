package com.chatbi.chat.service;

import com.chatbi.schema.service.SchemaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqlGuardServiceTest {

    @Mock
    private SchemaService schemaService;

    @InjectMocks
    private SqlGuardService sqlGuardService;

    @Test
    void shouldAllowSelectAndWrapLimit() {
        when(schemaService.listProjectTableNames(1L)).thenReturn(Set.of("demo_orders"));

        SqlGuardService.GuardedSql guardedSql = sqlGuardService.guard("SELECT id, amount FROM demo_orders ORDER BY id DESC", 1L);

        assertTrue(guardedSql.getBoundedSql().contains("chatbi_guard LIMIT 101"));
        assertTrue(guardedSql.getReferencedTables().contains("demo_orders"));
    }

    @Test
    void shouldRejectMutationKeywords() {
        assertThrows(IllegalArgumentException.class,
                () -> sqlGuardService.guard("UPDATE demo_orders SET amount = 1", 1L));
    }

    @Test
    void shouldRejectUnknownProjectTable() {
        when(schemaService.listProjectTableNames(1L)).thenReturn(Set.of("demo_orders"));

        assertThrows(IllegalArgumentException.class,
                () -> sqlGuardService.guard("SELECT * FROM sys_user", 1L));
    }
}
