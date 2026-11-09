package app.bizu.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

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
    void jidNoFormatoLidFicaVisivelParaOLog() throws Exception {
        var w = ler("""
                {"event":"messages.upsert","data":{"key":{"remoteJid":"98765@lid","fromMe":false,"id":"A4"},
                 "message":{"conversation":"Oi"}}}""");

        assertThat(w.telefone()).isEmpty();
        assertThat(w.jid()).contains("98765@lid");
    }

    @Test
    void contatosSalvosVindosDeUmaListaSoConsideramConversaIndividual() throws Exception {
        var w = ler("""
                {"event":"contacts.upsert","data":[
                  {"remoteJid":"5511999990002@s.whatsapp.net","pushName":"Mãe"},
                  {"remoteJid":"120363@g.us"},
                  {"id":"5511999990003@s.whatsapp.net"}]}""");

        assertThat(w.ehContatos()).isTrue();
        assertThat(w.ehNovaMensagem()).isFalse();
        assertThat(w.telefonesDeContatos()).containsExactly("5511999990002", "5511999990003");
    }

    @Test
    void eventoComDadosEmOutroFormatoNaoQuebra() throws Exception {
        var w = ler("""
                {"event":"connection.update","data":{"state":"open"}}""");

        assertThat(w.ehNovaMensagem()).isFalse();
        assertThat(w.ehContatos()).isFalse();
        assertThat(w.telefonesDeContatos()).isEmpty();
        assertThat(w.telefone()).isEmpty();
        assertThat(w.texto()).isEmpty();
    }
}
