package app.bizu.conversa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.bizu.conversa.HistoricoDoCorretor.MensagemEnviada;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EstiloServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-09T12:00:00Z");

    private HistoricoDoCorretor historico;
    private MensagemRepository mensagens;
    private Agente agente;
    private PromptService prompts;
    private EstiloService estilo;

    @BeforeEach
    void preparar() {
        historico = mock(HistoricoDoCorretor.class);
        mensagens = mock(MensagemRepository.class);
        agente = mock(Agente.class);
        prompts = mock(PromptService.class);
        estilo = new EstiloService(historico, mensagens, agente, prompts);
    }

    private static List<MensagemEnviada> mensagens(int quantidade) {
        return IntStream.rangeClosed(1, quantidade).mapToObj(i -> new MensagemEnviada("ID-" + i, "Fala, tudo certo? " + i)).toList();
    }

    @Test
    void poucasMensagensNaoGeramPerfilEOTomFormalContinua() {
        when(historico.mensagensEnviadas(anyString(), anyInt())).thenReturn(mensagens(EstiloService.MINIMO_MENSAGENS - 1));

        assertThat(estilo.gerar("c1", AGORA)).isFalse();

        verify(agente, never()).perfilDeEstilo(anyList());
        verify(prompts, never()).salvarEstilo(anyString(), anyString(), any());
    }

    @Test
    void geraESalvaOPerfilQuandoHaMensagensSuficientes() {
        when(historico.mensagensEnviadas(anyString(), anyInt())).thenReturn(mensagens(EstiloService.MINIMO_MENSAGENS));
        when(agente.perfilDeEstilo(anyList())).thenReturn(Optional.of("Tom informal e direto."));

        assertThat(estilo.gerar("c1", AGORA)).isTrue();

        verify(prompts).salvarEstilo("c1", "Tom informal e direto.", AGORA);
    }

    @Test
    void mensagensQueAPropriaIAEnviouNaoEntramNoPerfil() {
        when(historico.mensagensEnviadas(anyString(), anyInt())).thenReturn(mensagens(EstiloService.MINIMO_MENSAGENS + 5));
        when(mensagens.existeSaidaIaPorWaId("ID-1")).thenReturn(true);
        when(mensagens.existeSaidaIaPorWaId("ID-2")).thenReturn(true);
        when(agente.perfilDeEstilo(anyList())).thenReturn(Optional.of("Perfil"));

        estilo.gerar("c1", AGORA);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> enviadas = ArgumentCaptor.forClass(List.class);
        verify(agente).perfilDeEstilo(enviadas.capture());
        assertThat(enviadas.getValue()).hasSize(EstiloService.MINIMO_MENSAGENS + 3)
                .noneMatch(t -> t.endsWith(" 1") || t.endsWith(" 2"));
    }

    @Test
    void ficarAbaixoDoMinimoDepoisDeTirarAsDaIANaoGeraPerfil() {
        when(historico.mensagensEnviadas(anyString(), anyInt())).thenReturn(mensagens(EstiloService.MINIMO_MENSAGENS));
        when(mensagens.existeSaidaIaPorWaId("ID-1")).thenReturn(true);

        assertThat(estilo.gerar("c1", AGORA)).isFalse();
    }

    @Test
    void mensagemEnormeEBrancasSaoTratadas() {
        var lista = new java.util.ArrayList<>(mensagens(EstiloService.MINIMO_MENSAGENS));
        lista.add(new MensagemEnviada("X", "a".repeat(5000)));
        lista.add(new MensagemEnviada("Y", "   "));
        when(historico.mensagensEnviadas(anyString(), anyInt())).thenReturn(lista);
        when(agente.perfilDeEstilo(anyList())).thenReturn(Optional.of("Perfil"));

        estilo.gerar("c1", AGORA);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> enviadas = ArgumentCaptor.forClass(List.class);
        verify(agente).perfilDeEstilo(enviadas.capture());
        assertThat(enviadas.getValue()).hasSize(EstiloService.MINIMO_MENSAGENS + 1)
                .allMatch(t -> t.length() <= EstiloService.TAMANHO_MAXIMO_MENSAGEM);
    }

    @Test
    void agenteSemPerfilNaoSalvaNada() {
        when(historico.mensagensEnviadas(anyString(), anyInt())).thenReturn(mensagens(EstiloService.MINIMO_MENSAGENS));
        when(agente.perfilDeEstilo(anyList())).thenReturn(Optional.empty());

        assertThat(estilo.gerar("c1", AGORA)).isFalse();

        verify(prompts, never()).salvarEstilo(anyString(), anyString(), any());
    }
}
