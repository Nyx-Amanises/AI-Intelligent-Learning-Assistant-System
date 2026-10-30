package com.aiassistant.learning.service.task;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.common.security.DefaultAdminCredentialGuard;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SuppressWarnings({"unchecked", "rawtypes"})
class AdminCredentialBootstrapRunnerTest {

    // V3 中已公开的历史种子哈希；仅作回归测试。
    private static final String SEED_HASH = "$2a$10$kkQN2HTakw8VlHKRJmx7rujZkYZbmMbLShHG6shrcLTsjew/8YkYu";
    private static final String PRIVATE_PASSWORD = "private-Test-2026";
    private final SysUserMapper users = mock(SysUserMapper.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private SysUser admin;

    @BeforeAll
    static void initializeMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), SysUser.class);
    }

    @BeforeEach
    void setUp() {
        admin = new SysUser();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setRoleCode("ADMIN");
        admin.setPasswordHash(SEED_HASH);
        admin.setStatus(1);
        admin.setTokenVersion(2);
        when(users.selectList(any(Wrapper.class))).thenReturn(List.of(admin));
        when(users.update(isNull(), any(Wrapper.class))).thenReturn(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, -1})
    void initializesOnlyTheDefaultAdminAndRevokesExistingTokens(int status) {
        admin.setStatus(status);
        assertTrue(DefaultAdminCredentialGuard.isBlocked(admin));

        runner(PRIVATE_PASSWORD).run(null);

        LambdaUpdateWrapper<SysUser> update = capturedUpdate();
        assertTrue(update.getSqlSet().contains("password_hash="));
        assertTrue(update.getSqlSet().contains("status="));
        assertTrue(update.getSqlSet().contains("token_version="));
        assertTrue(update.getSqlSegment().contains("password_hash ="));
        assertTrue(update.getSqlSegment().contains("token_version ="));
        assertTrue(update.getSqlSegment().contains("status ="));
        assertTrue(update.getSqlSegment().contains("role_code ="));
        String hash = newHash(update);
        assertTrue(encoder.matches(PRIVATE_PASSWORD, hash));
        assertFalse(encoder.matches("123456", hash));
        assertTrue(update.getParamNameValuePairs().containsValue(3));
        assertFalse(update.getParamNameValuePairs().containsValue(PRIVATE_PASSWORD));

        admin.setPasswordHash(hash);
        assertFalse(DefaultAdminCredentialGuard.isBlocked(admin));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingPasswordLocksTheDefaultAccountWithoutInventingCredentials(String password) {
        runner(password).run(null);

        LambdaUpdateWrapper<SysUser> update = capturedUpdate();
        assertTrue(update.getParamNameValuePairs().containsValue(-1));
        assertTrue(update.getParamNameValuePairs().containsValue(3));
        assertEquals(SEED_HASH, newHash(update));
    }

    @Test
    void aFreshlySaltedPublicPasswordIsAlsoLockedWhenMigrationsAreSkipped() {
        admin.setPasswordHash(encoder.encode("123456"));
        runner("").run(null);

        assertTrue(capturedUpdate().getParamNameValuePairs().containsValue(-1));
    }

    @Test
    void alreadyLockedAccountDoesNotKeepBumpingItsTokenVersion() {
        admin.setStatus(-1);
        runner("").run(null);
        verify(users, never()).update(isNull(), any(Wrapper.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456", "short1", "abcdefghijklmn", "1234567890123", "${MISSING_SECRET_123}"})
    void invalidBootstrapPasswordFailsWithoutLoggingOrSavingIt(String password) {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> runner(password).run(null));
        assertFalse(error.getMessage().contains(password));
        verify(users, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void refusesPasswordsThatBcryptWouldTruncate() {
        assertThrows(IllegalStateException.class, () -> runner("LongPass1" + "中".repeat(24)).run(null));
        verify(users, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void existingPrivatePasswordIsNeverResetEvenIfEnvironmentValueChangesOrIsInvalid() {
        admin.setPasswordHash(encoder.encode("previousPrivate2026"));
        assertDoesNotThrow(() -> runner("invalid").run(null));
        verify(users, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void intentionallyDisabledDefaultAdminIsNotReenabled() {
        admin.setStatus(0);
        runner(PRIVATE_PASSWORD).run(null);
        verify(users, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void bootstrapPasswordDoesNotResetOtherAdminAccounts() {
        admin.setUsername("another-admin");
        runner(PRIVATE_PASSWORD).run(null);
        LambdaUpdateWrapper<SysUser> update = capturedUpdate();
        assertEquals(SEED_HASH, newHash(update));
        assertTrue(update.getParamNameValuePairs().containsValue(-1));
    }

    @Test
    void ordinaryUsersAreNotLockedEvenIfReturnedByTheMapper() {
        admin.setRoleCode("USER");
        runner(PRIVATE_PASSWORD).run(null);
        verify(users, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void concurrentChangeStopsInitializationInsteadOfOverwritingTheAccount() {
        when(users.update(isNull(), any(Wrapper.class))).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> runner(PRIVATE_PASSWORD).run(null));
    }

    private AdminCredentialBootstrapRunner runner(String password) {
        return new AdminCredentialBootstrapRunner(users, encoder, password);
    }

    private LambdaUpdateWrapper<SysUser> capturedUpdate() {
        ArgumentCaptor<Wrapper<SysUser>> capture = ArgumentCaptor.forClass(Wrapper.class);
        verify(users).update(isNull(), capture.capture());
        LambdaUpdateWrapper<SysUser> update = (LambdaUpdateWrapper<SysUser>) capture.getValue();
        update.getSqlSegment();
        return update;
    }

    private String newHash(LambdaUpdateWrapper<SysUser> update) {
        // 第一个 SET 参数是实际要写入的哈希，与 WHERE 中的旧哈希分开验证。
        String parameter = update.getSqlSet().split(",")[0];
        String name = parameter.substring(parameter.indexOf("MPGENVAL"), parameter.indexOf('}'));
        return (String) update.getParamNameValuePairs().get(name);
    }
}
