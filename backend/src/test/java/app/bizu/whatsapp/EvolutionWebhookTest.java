package app.bizu.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import app.bizu.conversa.ConversaService.ContatoSalvo;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class EvolutionWebhookTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private static EvolutionWebhook ler(String json) throws Exception {
        return JSON.readValue(json, EvolutionWebhook.class);
    }

    @Test
    void mensagemDeTextoDeConversaIndividual() throws Exception {
        var w = ler("""
                {"event":"messages.upsert","data":{"key":{"remoteJid":"5511999990001@s.whatsapp.net","fromMe":false,"id":"A1"},
                 "message":{"conversation":"Olá"}}}""");

        assertThat(w.ehNovaMensagem()).isTrue();
        assertThat(w.telefone()).contains("5511999990001");
        assertThat(w.texto()).contains("Olá");
        assertThat(w.fromMe()).isFalse();
        assertThat(w.idMensagem()).isEqualTo("A1");
    }

    @Test
    void textoEstendidoEEventoEmMaiusculasComSublinhado() throws Exception {
        var w = ler("""
                {"event":"MESSAGES_UPSERT","data":{"key":{"remoteJid":"5511999990001@s.whatsapp.net","fromMe":true,"id":"A2"},
                 "message":{"extendedTextMessage":{"text":"Oi, sou o corretor"}}}}""");

        assertThat(w.ehNovaMensagem()).isTrue();
        assertThat(w.texto()).contains("Oi, sou o corretor");
        assertThat(w.fromMe()).isTrue();
    }

    @Test
    void grupoEStatusNaoViramTelefone() throws Exception {
        var grupo = ler("""
                {"event":"messages.upsert","data":{"key":{"remoteJid":"120363@g.us","fromMe":false,"id":"A3"},
                 "message":{"conversation":"oi grupo"}}}""");

        assertThat(grupo.telefone()).isEmpty();
        assertThat(grupo.jid()).contains("120363@g.us");
    }

    @Test
    void contatoSoComCodigoLidViraOProprioCodigoComoIdentificador() throws Exception {
        var w = ler("""
                {"event":"messages.upsert","data":{"key":{"remoteJid":"98765@lid","fromMe":false,"id":"A4"},
                 "message":{"conversation":"Oi"}}}""");

        assertThat(w.telefone()).contains("98765@lid");
        assertThat(w.jid()).contains("98765@lid");
    }

    @Test
    void lidComTelefoneNoJidAlternativoUsaOTelefone() throws Exception {
        var w = ler("""
                {"event":"messages.upsert","data":{"key":{"remoteJid":"98765@lid","remoteJidAlt":"5511999990004@s.whatsapp.net",
                 "fromMe":false,"id":"A5"},"message":{"conversation":"Oi"}}}""");

        assertThat(w.telefone()).contains("5511999990004");
    }

    @Test
    void nomeDoContatoVemDoPushNameSoEmMensagemDoCliente() throws Exception {
        var cliente = ler("""
                {"event":"messages.upsert","data":{"pushName":"Ana Souza","key":{"remoteJid":"5511999990001@s.whatsapp.net",
                 "fromMe":false,"id":"A6"},"message":{"conversation":"Oi"}}}""");
        var corretor = ler("""
                {"event":"messages.upsert","data":{"pushName":"Meu Nome","key":{"remoteJid":"5511999990001@s.whatsapp.net",
                 "fromMe":true,"id":"A7"},"message":{"conversation":"Oi"}}}""");

        assertThat(cliente.nomeDoContato()).contains("Ana Souza");
        assertThat(corretor.nomeDoContato()).isEmpty();
    }

    @Test
    void contatosSalvosVindosDeUmaListaSoConsideramConversaIndividual() throws Exception {
        var w = ler("""
                {"event":"contacts.upsert","data":[
                  {"remoteJid":"5511999990002@s.whatsapp.net","pushName":"Mãe"},
                  {"remoteJid":"120363@g.us"},
                  {"id":"5511999990003@s.whatsapp.net"},
                  {"remoteJid":"555@lid","remoteJidAlt":"5511999990005@s.whatsapp.net","pushName":"Joana"},
                  {"remoteJid":"777@lid"}]}""");

        assertThat(w.ehContatos()).isTrue();
        assertThat(w.ehNovaMensagem()).isFalse();
        assertThat(w.contatos()).containsExactly(
                new ContatoSalvo("5511999990002", "Mãe"),
                new ContatoSalvo("5511999990003", null),
                new ContatoSalvo("5511999990005", "Joana"),
                new ContatoSalvo("777@lid", null));
    }

    @Test
    void eventoComDadosEmOutroFormatoNaoQuebra() throws Exception {
        var w = ler("""
                {"event":"connection.update","data":{"state":"open"}}""");

        assertThat(w.ehNovaMensagem()).isFalse();
        assertThat(w.ehContatos()).isFalse();
        assertThat(w.contatos()).isEmpty();
        assertThat(w.telefone()).isEmpty();
        assertThat(w.texto()).isEmpty();
    }
}
