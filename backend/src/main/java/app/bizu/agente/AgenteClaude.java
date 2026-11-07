package app.bizu.agente;

import app.bizu.agente.MontadorDeConversa.Turno;
import app.bizu.conversa.Agente;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Agente que responde pela API da Anthropic. A chave é lida de ANTHROPIC_API_KEY. Qualquer falha de
 * resposta (texto vazio, recusa, excesso de rodadas de ferramentas) devolve a conversa ao corretor em vez de
 * deixar o cliente sem resposta e a conversa travada no BOT.
 */
@Component
@ConditionalOnProperty(name = "bizu.agente.tipo", havingValue = "claude")
class AgenteClaude implements Agente {

    private static final Logger log = LoggerFactory.getLogger(AgenteClaude.class);
    private static final int MAXIMO_RODADAS = 4;

    private final AnthropicClient cliente = AnthropicOkHttpClient.fromEnv();
    private final AgenteProperties props;

    AgenteClaude(AgenteProperties props) {
        this.props = props;
    }

    @Override
    public RespostaAgente responder(Contexto contexto) {
        List<Turno> turnos = MontadorDeConversa.turnos(contexto.historico());
        if (turnos.isEmpty() || !turnos.get(turnos.size() - 1).doUsuario()) {
            return RespostaAgente.passar();
        }

        MessageCreateParams.Builder pedido = MessageCreateParams.builder()
                .model(props.modelo())
                .maxTokens(props.maxTokens())
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(MontadorDeConversa.sistema(contexto.promptBase(), contexto.promptEstilo()))
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
                .addTool(registrarQualificacao())
                .addTool(registrarImovelCitado())
                .addTool(passarAoCorretor());
        for (Turno turno : turnos) {
            if (turno.doUsuario()) {
                pedido.addUserMessage(turno.texto());
            } else {
                pedido.addAssistantMessage(turno.texto());
            }
        }

        Acumulador acumulado = new Acumulador();
        for (int rodada = 0; rodada < MAXIMO_RODADAS; rodada++) {
            Message resposta = cliente.messages().create(pedido.build());
            List<ToolUseBlock> usos = resposta.content().stream()
                    .flatMap(bloco -> bloco.toolUse().stream())
                    .toList();

            if (usos.isEmpty()) {
                String texto = texto(resposta);
                if (texto.isBlank()) {
                    log.warn("O modelo não devolveu texto (stop_reason={}); a conversa vai ao corretor",
                            resposta.stopReason());
                    return new RespostaAgente(null, acumulado.qualificacao(), true);
                }
                return new RespostaAgente(texto, acumulado.qualificacao(), false);
            }

            List<ContentBlockParam> resultados = new ArrayList<>();
            for (ToolUseBlock uso : usos) {
                acumulado.aplicar(uso.name(), uso);
                resultados.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(uso.id())
                        .content("ok")
                        .build()));
            }
            if (acumulado.passar) {
                return new RespostaAgente(null, acumulado.qualificacao(), true);
            }
            pedido.addMessage(resposta);
            pedido.addUserMessageOfContentBlockParams(resultados);
        }

        log.warn("O modelo não terminou depois de {} rodadas de ferramentas; a conversa vai ao corretor",
                MAXIMO_RODADAS);
        return new RespostaAgente(null, acumulado.qualificacao(), true);
    }

    private static String texto(Message resposta) {
        StringBuilder sb = new StringBuilder();
        resposta.content().forEach(bloco -> bloco.text().ifPresent(t -> {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(t.text());
        }));
        return sb.toString().strip();
    }

    /** Junta o que as ferramentas registraram durante a conversa com o modelo. */
    private static final class Acumulador {
        String finalidade;
        String regiao;
        String faixaValor;
        String prazo;
        String imovelCitado;
        boolean passar;

        void aplicar(String ferramenta, ToolUseBlock uso) {
            switch (ferramenta) {
                case "registrar_qualificacao" -> {
                    finalidade = novo(finalidade, campo(uso, "finalidade"));
                    regiao = novo(regiao, campo(uso, "regiao"));
                    faixaValor = novo(faixaValor, campo(uso, "faixa_valor"));
                    prazo = novo(prazo, campo(uso, "prazo"));
                }
                case "registrar_imovel_citado" -> imovelCitado = novo(imovelCitado, campo(uso, "imovel"));
                case "passar_ao_corretor" -> passar = true;
                default -> log.warn("Ferramenta desconhecida pedida pelo modelo: {}", ferramenta);
            }
        }

        Qualificacao qualificacao() {
            Qualificacao q = new Qualificacao(finalidade, regiao, faixaValor, prazo, imovelCitado);
            return q.vazia() ? null : q;
        }

        private static String novo(String atual, String recebido) {
            return recebido != null ? recebido : atual;
        }
    }

    private static String campo(ToolUseBlock uso, String nome) {
        Optional<? extends Map<String, ? extends JsonValue>> entrada = uso._input().asObject();
        if (entrada.isEmpty()) {
            return null;
        }
        JsonValue valor = entrada.get().get(nome);
        if (valor == null) {
            return null;
        }
        Object bruto = valor.asString().orElse(null);
        String texto = bruto == null ? null : bruto.toString();
        return texto == null || texto.isBlank() ? null : texto;
    }

    private static Tool registrarQualificacao() {
        return ferramenta("registrar_qualificacao",
                "Registra o que o cliente informou sobre o que procura. Preencha só os campos que ele disse.",
                Map.of("finalidade", "compra, venda ou aluguel",
                        "regiao", "bairro ou região de interesse",
                        "faixa_valor", "faixa de valor que o cliente mencionou",
                        "prazo", "prazo ou urgência que o cliente mencionou"),
                List.of());
    }

    private static Tool registrarImovelCitado() {
        return ferramenta("registrar_imovel_citado",
                "Registra um imóvel específico que o cliente disse já ter visto ou que quer saber mais.",
                Map.of("imovel", "descrição ou identificação do imóvel, como o cliente falou"),
                List.of("imovel"));
    }

    private static Tool passarAoCorretor() {
        return ferramenta("passar_ao_corretor",
                "Entrega a conversa ao corretor e para de responder. Não envia nenhuma mensagem ao cliente.",
                Map.of("motivo", "motivo curto da passagem"),
                List.of());
    }

    private static Tool ferramenta(String nome, String descricao, Map<String, String> campos,
                                   List<String> obrigatorios) {
        Tool.InputSchema.Properties.Builder propriedades = Tool.InputSchema.Properties.builder();
        Map<String, String> ordenados = new LinkedHashMap<>(campos);
        ordenados.forEach((campo, desc) -> propriedades.putAdditionalProperty(campo,
                JsonValue.from(Map.of("type", "string", "description", desc))));
        return Tool.builder()
                .name(nome)
                .description(descricao)
                .inputSchema(Tool.InputSchema.builder()
                        .properties(propriedades.build())
                        .required(obrigatorios)
                        .build())
                .build();
    }
}
