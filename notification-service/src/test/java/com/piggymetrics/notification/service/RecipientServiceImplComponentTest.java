package com.piggymetrics.notification.service;

import com.piggymetrics.notification.domain.Frequency;
import com.piggymetrics.notification.domain.NotificationSettings;
import com.piggymetrics.notification.domain.NotificationType;
import com.piggymetrics.notification.domain.Recipient;
import com.piggymetrics.notification.repository.RecipientRepository;
import com.piggymetrics.notification.support.TestCaseId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/**
 * Verificacion a nivel de componente de {@link RecipientServiceImpl} (componente #6).
 *
 * Aislamiento: RecipientRepository se reemplaza por un mock de Mockito; no se levanta
 * contexto Spring ni MongoDB. El reloj (new Date()) se controla acotando el valor
 * esperado entre dos instantes tomados antes y despues de la llamada, de modo que el
 * veredicto es determinista. Cada prueba lleva el ID de su caso con @TestCaseId (RSI-xx).
 */
@RunWith(MockitoJUnitRunner.class)
public class RecipientServiceImplComponentTest {

	private static final Date FECHA_FIJA = new Date(1577836800000L); // 2020-01-01T00:00:00Z

	@InjectMocks
	private RecipientServiceImpl service;

	@Mock
	private RecipientRepository repository;

	private Recipient recipient;

	@Before
	public void setUp() {
		recipient = new Recipient();
		recipient.setEmail("ser@test.com");
		recipient.setScheduledNotifications(new EnumMap<>(NotificationType.class));
	}

	// ---------------------------------------------------------------- findByAccountName

	@Test
	@TestCaseId("RSI-01")
	public void RSI_01_findByAccountName_nombreValido_delegaEnRepositorio() {
		Recipient esperado = new Recipient();
		when(repository.findByAccountName("demo")).thenReturn(esperado);

		Recipient encontrado = service.findByAccountName("demo");

		assertThat(encontrado).isSameAs(esperado);
		verify(repository, times(1)).findByAccountName("demo");
		verifyNoMoreInteractions(repository);
	}

	@Test
	@TestCaseId("RSI-02")
	public void RSI_02_findByAccountName_longitudUno_esValido() {
		when(repository.findByAccountName("a")).thenReturn(recipient);

		assertThat(service.findByAccountName("a")).isSameAs(recipient);
		verify(repository).findByAccountName("a");
	}

	@Test
	@TestCaseId("RSI-03")
	public void RSI_03_findByAccountName_cadenaVacia_lanzaIllegalArgument() {
		assertThatThrownBy(() -> service.findByAccountName(""))
				.isInstanceOf(IllegalArgumentException.class);
		verifyZeroInteractions(repository);
	}

	@Test
	@TestCaseId("RSI-04")
	public void RSI_04_findByAccountName_nulo_lanzaIllegalArgument() {
		assertThatThrownBy(() -> service.findByAccountName(null))
				.isInstanceOf(IllegalArgumentException.class);
		verifyZeroInteractions(repository);
	}

	@Test
	@TestCaseId("RSI-05")
	public void RSI_05_findByAccountName_soloEspacios_seAceptaYDelega() {
		// Assert.hasLength no rechaza espacios en blanco (hasText si lo haria).
		service.findByAccountName(" ");

		verify(repository).findByAccountName(" ");
	}

	@Test
	@TestCaseId("RSI-06")
	public void RSI_06_findByAccountName_cuentaInexistente_retornaNull() {
		when(repository.findByAccountName("noexiste")).thenReturn(null);

		assertThat(service.findByAccountName("noexiste")).isNull();
	}

	// ---------------------------------------------------------------- save

	@Test
	@TestCaseId("RSI-07")
	public void RSI_07_save_lastNotifiedNulo_seInicializaConFechaActual() {
		NotificationSettings remind = settings(true, Frequency.WEEKLY, null);
		recipient.getScheduledNotifications().put(NotificationType.REMIND, remind);

		Date antes = new Date();
		service.save("demo", recipient);
		Date despues = new Date();

		assertThat(remind.getLastNotified()).isBetween(antes, despues, true, true);
	}

	@Test
	@TestCaseId("RSI-08")
	public void RSI_08_save_lastNotifiedExistente_noSeSobrescribe() {
		Date previa = new Date(FECHA_FIJA.getTime());
		NotificationSettings backup = settings(false, Frequency.MONTHLY, previa);
		recipient.getScheduledNotifications().put(NotificationType.BACKUP, backup);

		service.save("demo", recipient);

		assertThat(backup.getLastNotified()).isSameAs(previa).isEqualTo(FECHA_FIJA);
	}

	@Test
	@TestCaseId("RSI-09")
	public void RSI_09_save_mapaMixto_soloInicializaLosNulos_yFijaAccountName() {
		Date previa = new Date(FECHA_FIJA.getTime());
		NotificationSettings backup = settings(true, Frequency.QUARTERLY, previa);
		NotificationSettings remind = settings(true, Frequency.WEEKLY, null);
		recipient.getScheduledNotifications().put(NotificationType.BACKUP, backup);
		recipient.getScheduledNotifications().put(NotificationType.REMIND, remind);
		recipient.setAccountName("otro");

		Date antes = new Date();
		Recipient resultado = service.save("demo", recipient);
		Date despues = new Date();

		assertThat(resultado).isSameAs(recipient);
		assertThat(resultado.getAccountName()).isEqualTo("demo");
		assertThat(backup.getLastNotified()).isSameAs(previa);
		assertThat(remind.getLastNotified()).isBetween(antes, despues, true, true);
		verify(repository, times(1)).save(recipient);
		verifyNoMoreInteractions(repository);
	}

	@Test
	@TestCaseId("RSI-10")
	public void RSI_10_save_mapaVacio_persisteSinCambiosEnSettings() {
		Recipient resultado = service.save("demo", recipient);

		assertThat(resultado.getScheduledNotifications()).isEmpty();
		assertThat(resultado.getAccountName()).isEqualTo("demo");
		verify(repository).save(recipient);
	}

	@Test
	@TestCaseId("RSI-11")
	public void RSI_11_save_scheduledNotificationsNulo_lanzaNPE_ySinPersistir() {
		recipient.setScheduledNotifications(null);

		assertThatThrownBy(() -> service.save("demo", recipient))
				.isInstanceOf(NullPointerException.class);
		verify(repository, never()).save(any(Recipient.class));
	}

	@Test
	@TestCaseId("RSI-12")
	public void RSI_12_save_recipientNulo_lanzaNPE_ySinPersistir() {
		assertThatThrownBy(() -> service.save("demo", null))
				.isInstanceOf(NullPointerException.class);
		verifyZeroInteractions(repository);
	}

	@Test
	@TestCaseId("RSI-13")
	public void RSI_13_save_accountNameNulo_seAceptaSinValidar() {
		// A diferencia de findByAccountName, save no valida el nombre de cuenta.
		Recipient resultado = service.save(null, recipient);

		assertThat(resultado.getAccountName()).isNull();
		verify(repository).save(recipient);
	}

	@Test
	@TestCaseId("RSI-14")
	public void RSI_14_save_dosVeces_conservaLaFechaDeLaPrimera() {
		NotificationSettings remind = settings(true, Frequency.WEEKLY, null);
		recipient.getScheduledNotifications().put(NotificationType.REMIND, remind);

		service.save("demo", recipient);
		Date primera = remind.getLastNotified();
		service.save("demo", recipient);

		assertThat(primera).isNotNull();
		assertThat(remind.getLastNotified()).isSameAs(primera);
		verify(repository, times(2)).save(recipient);
	}

	// ---------------------------------------------------------------- findReadyToNotify

	@Test
	@TestCaseId("RSI-15")
	public void RSI_15_findReadyToNotify_backup_consultaSoloBackup() {
		List<Recipient> lista = Collections.singletonList(recipient);
		when(repository.findReadyForBackup()).thenReturn(lista);

		assertThat(service.findReadyToNotify(NotificationType.BACKUP)).isSameAs(lista);
		verify(repository).findReadyForBackup();
		verifyNoMoreInteractions(repository);
	}

	@Test
	@TestCaseId("RSI-16")
	public void RSI_16_findReadyToNotify_remind_consultaSoloRemind() {
		List<Recipient> lista = Collections.singletonList(recipient);
		when(repository.findReadyForRemind()).thenReturn(lista);

		assertThat(service.findReadyToNotify(NotificationType.REMIND)).isSameAs(lista);
		verify(repository).findReadyForRemind();
		verifyNoMoreInteractions(repository);
	}

	@Test
	@TestCaseId("RSI-17")
	public void RSI_17_findReadyToNotify_tipoNulo_lanzaNPE_noIllegalArgument() {
		// El switch sobre un enum nulo falla antes de llegar a la rama default.
		assertThatThrownBy(() -> service.findReadyToNotify(null))
				.isInstanceOf(NullPointerException.class);
		verifyZeroInteractions(repository);
	}

	// ---------------------------------------------------------------- markNotified

	@Test
	@TestCaseId("RSI-18")
	public void RSI_18_markNotified_actualizaSoloElTipoIndicado() {
		Date previaBackup = new Date(FECHA_FIJA.getTime());
		NotificationSettings backup = settings(true, Frequency.MONTHLY, previaBackup);
		NotificationSettings remind = settings(true, Frequency.WEEKLY, null);
		recipient.getScheduledNotifications().put(NotificationType.BACKUP, backup);
		recipient.getScheduledNotifications().put(NotificationType.REMIND, remind);

		Date antes = new Date();
		service.markNotified(NotificationType.REMIND, recipient);
		Date despues = new Date();

		assertThat(remind.getLastNotified()).isBetween(antes, despues, true, true);
		assertThat(backup.getLastNotified()).isSameAs(previaBackup);
		verify(repository, times(1)).save(recipient);
		verifyNoMoreInteractions(repository);
	}

	@Test
	@TestCaseId("RSI-19")
	public void RSI_19_markNotified_fechaPrevia_seSobrescribeConFechaActual() {
		NotificationSettings backup = settings(true, Frequency.WEEKLY, new Date(FECHA_FIJA.getTime()));
		recipient.getScheduledNotifications().put(NotificationType.BACKUP, backup);

		Date antes = new Date();
		service.markNotified(NotificationType.BACKUP, recipient);
		Date despues = new Date();

		assertThat(backup.getLastNotified()).isAfter(FECHA_FIJA).isBetween(antes, despues, true, true);
	}

	@Test
	@TestCaseId("RSI-20")
	public void RSI_20_markNotified_tipoAusenteEnMapa_lanzaNPE_ySinPersistir() {
		recipient.getScheduledNotifications()
				.put(NotificationType.REMIND, settings(true, Frequency.WEEKLY, null));

		assertThatThrownBy(() -> service.markNotified(NotificationType.BACKUP, recipient))
				.isInstanceOf(NullPointerException.class);
		verifyZeroInteractions(repository);
	}

	@Test
	@TestCaseId("RSI-21")
	public void RSI_21_markNotified_recipientNulo_lanzaNPE() {
		assertThatThrownBy(() -> service.markNotified(NotificationType.REMIND, null))
				.isInstanceOf(NullPointerException.class);
		verifyZeroInteractions(repository);
	}

	// ---------------------------------------------------------------- utilidades

	private static NotificationSettings settings(boolean active, Frequency frequency, Date lastNotified) {
		NotificationSettings s = new NotificationSettings();
		s.setActive(active);
		s.setFrequency(frequency);
		s.setLastNotified(lastNotified);
		return s;
	}
}
