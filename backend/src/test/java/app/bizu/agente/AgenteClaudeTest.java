package app.bizu.agente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.bizu.conversa.Agente.Contexto;
import app.bizu.conversa.Agente.RespostaAgente;
import app.bizu.conversa.Agente.Trecho;
import com.anthropic.client.AnthropicClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.services.blocking.MessageService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** O laço de ferramentas do agente, com o cliente da Anthropic simulado (sem rede, sem chave). */
class AgenteClaudeTest {

    private MessageService servico;
    private AgenteClaude agente;

    @BeforeEach
    void preparar() {
        AnthropicClient cliente = mock(AnthropicClient.class);
        servico = mock(MessageService.class);
        when(cliente.messages()).thenReturn(servico);
        agente = new AgenteClaude(new AgenteProperties("claude", "modelo-de-teste", 1024), cliente);
    }

    private static Message texto(String texto) throws Exception {
        return mensagem("{\"type\":\"text\",\"text\":\"" + texto + "\"}", "end_turn");
    }

    private static Message ferramenta(String nome, String entrada) throws Exception {
        return mensagem("{\"type\":\"tool_use\",\"id\":\"t-" + nome + "\",\"name\":\"" + nome + "\",\"input\":"
                + entrada + "}", "tool_use");
    }

    private static Message mensagem(String bloco, String motivo) throws Exception {
        String json = """
                {"id":"m1","type":"message","role":"assistant","model":"modelo-de-teste","content":[%s],
                 "stop_reason":"%s","stop_sequence":null,"usage":{"input_tokens":1,"output_tokens":1}}"""
                .formatted(bloco, motivo);
        return ObjectMappers.jsonMapper().readValue(json, Message.class);
    }

    private static Contexto conversa() {
        return new Contexto(null, List.of(new Trecho(true, "Quero comprar no Centro")), "", "");
    }

    @Test
    void respostaDeTextoVaiAoCliente() throws Exception {
        when(servico.create(any(MessageCreateParams.class))).thenReturn(texto("Olá! Qual a sua faixa de valor?"));

        RespostaAgente resposta = agente.responder(conversa());

        assertThat(resposta.texto()).isEqualTo("Olá! Qual a sua faixa de valor?");
        assertThat(resposta.passarAoCorretor()).isFalse();
        assertThat(resposta.qualificacao()).isNull();
    }

    @Test
    void qualificacaoRegistradaPorFerramentaEAcumuladaAteARespostaFinal() throws Exception {
        when(servico.create(any(MessageCreateParams.class))).thenReturn(
                ferramenta("registrar_qualificacao", "{\"finalidade\":\"compra\",\"regiao\":\"Centro\"}"),
                ferramenta("registrar_imovel_citado", "{\"imovel\":\"apto da Rua das Flores\"}"),
                texto("Entendi, e qual o prazo?"));

        RespostaAgente resposta = agente.responder(conversa());

        assertThat(resposta.texto()).isEqualTo("Entendi, e qual o prazo?");
        assertThat(resposta.passarAoCorretor()).isFalse();
        assertThat(resposta.qualificacao().finalidade()).isEqualTo("compra");
        assertThat(resposta.qualificacao().regiao()).isEqualTo("Centro");
        assertThat(resposta.qualificacao().imovelCitado()).isEqualTo("apto da Rua das Flores");
        assertThat(resposta.qualificacao().prazo()).isNull();
        verify(servico, times(3)).create(any(MessageCreateParams.class));
    }

    @Test
    void passarAoCorretorNaoEnviaTextoEParaNaMesmaRodada() throws Exception {
        when(servico.create(any(MessageCreateParams.class)))
                .thenReturn(ferramenta("passar_ao_corretor", "{\"motivo\":\"pediu preço\"}"));

        RespostaAgente resposta = agente.responder(conversa());

        assertThat(resposta.passarAoCorretor()).isTrue();
        assertThat(resposta.texto()).isNull();
        verify(servico, times(1)).create(any(MessageCreateParams.class));
    }

    @Test
    void respostaSemTextoDevolveAConversaAoCorretor() throws Exception {
        when(servico.create(any(MessageCreateParams.class))).thenReturn(texto("   "));

        RespostaAgente resposta = agente.responder(conversa());

        assertThat(resposta.passarAoCorretor()).isTrue();
        assertThat(resposta.texto()).isNull();
    }

    @Test
    void modeloQueNuncaTerminaAsFerramentasPassaAoCorretorDepoisDeQuatroRodadas() throws Exception {
        when(servico.create(any(MessageCreateParams.class)))
                .thenReturn(ferramenta("registrar_imovel_citado", "{\"imovel\":\"x\"}"));

        RespostaAgente resposta = agente.responder(conversa());

        assertThat(resposta.passarAoCorretor()).isTrue();
        verify(servico, times(4)).create(any(MessageCreateParams.class));
    }

    @Test
    void semMensagemNovaDoClienteNaoChamaAAPI() {
        Contexto semCliente = new Contexto(null,
                List.of(new Trecho(true, "Oi"), new Trecho(false, "Olá, tudo bem?")), "", "");

        RespostaAgente resposta = agente.responder(semCliente);

        assertThat(resposta.passarAoCorretor()).isTrue();
        verify(servico, never()).create(any(MessageCreateParams.class));
    }

    @Test
    void perfilDeEstiloUsaSoAsMensagensDoCorretorEDevolveOTexto() throws Exception {
        when(servico.create(any(MessageCreateParams.class)))
                .thenReturn(texto("Tom informal, frases curtas, começa com Fala!"));

        var perfil = agente.perfilDeEstilo(List.of("Fala! Beleza?", "Bora marcar?"));

        assertThat(perfil).contains("Tom informal, frases curtas, começa com Fala!");
        ArgumentCaptor<MessageCreateParams> pedido = ArgumentCaptor.forClass(MessageCreateParams.class);
        verify(servico).create(pedido.capture());
        assertThat(pedido.getValue().toString()).contains("Fala! Beleza?").contains("Bora marcar?");
    }

    @Test
    void perfilDeEstiloVazioNaoViraPerfil() throws Exception {
        when(servico.create(any(MessageCreateParams.class))).thenReturn(texto(" "));

        assertThat(agente.perfilDeEstilo(List.of("Oi"))).isEmpty();
    }
}
