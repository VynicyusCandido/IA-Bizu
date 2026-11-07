package app.bizu.agente;

import static org.assertj.core.api.Assertions.assertThat;

import app.bizu.agente.MontadorDeConversa.Turno;
import app.bizu.conversa.Agente.Trecho;
import java.util.List;
import org.junit.jupiter.api.Test;

class MontadorDeConversaTest {

    @Test
    void mensagensSeguidasDoClienteViramUmTurnoSo() {
        List<Turno> turnos = MontadorDeConversa.turnos(List.of(
                new Trecho(true, "Oi"),
                new Trecho(true, "Quero comprar um apê")));

        assertThat(turnos).containsExactly(new Turno(true, "Oi\nQuero comprar um apê"));
    }

    @Test
    void clienteViraUserEIaEAssistantAlternando() {
        List<Turno> turnos = MontadorDeConversa.turnos(List.of(
                new Trecho(true, "Oi"),
                new Trecho(false, "Olá, em que posso ajudar?"),
                new Trecho(true, "Procuro casa")));

        assertThat(turnos).extracting(Turno::doUsuario).containsExactly(true, false, true);
    }

    @Test
    void conversaNuncaComecaPorTurnoDoAssistant() {
        List<Turno> turnos = MontadorDeConversa.turnos(List.of(
                new Trecho(false, "Mensagem solta"),
                new Trecho(true, "Oi")));

        assertThat(turnos).containsExactly(new Turno(true, "Oi"));
    }

    @Test
    void ignoraMensagensVazias() {
        List<Turno> turnos = MontadorDeConversa.turnos(List.of(
                new Trecho(true, "  "), new Trecho(true, "Oi")));

        assertThat(turnos).containsExactly(new Turno(true, "Oi"));
    }

    @Test
    void sistemaSempreTrazAsRegrasFixasMesmoComPromptEditado() {
        String sistema = MontadorDeConversa.sistema("Seja descontraído.", null);

        assertThat(sistema).startsWith("Seja descontraído.")
                .contains("Nunca invente preço")
                .contains("não afirme ser uma pessoa");
    }

    @Test
    void perfilDeEstiloEntraNoSistemaQuandoExiste() {
        String sistema = MontadorDeConversa.sistema("Base", "Frases curtas, sem pontuação final.");

        assertThat(sistema).contains("Estilo de escrita do corretor").contains("Frases curtas");
        assertThat(MontadorDeConversa.sistema("Base", " ")).doesNotContain("Estilo de escrita");
    }

    @Test
    void semPromptBaseUsaOPadraoFormal() {
        assertThat(MontadorDeConversa.sistema(null, null)).contains("formalidade");
    }
}
