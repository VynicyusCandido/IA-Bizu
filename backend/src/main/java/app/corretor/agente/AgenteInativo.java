package app.corretor.agente;

import app.corretor.conversa.Agente;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Agente padrão enquanto não há adaptador de IA configurado: não responde ninguém e deixa a conversa
 * com o corretor. É o comportamento seguro. O adaptador real do Claude entra com
 * {@code corretor.agente.tipo=claude}.
 */
@Component
@ConditionalOnProperty(name = "corretor.agente.tipo", havingValue = "inativo", matchIfMissing = true)
class AgenteInativo implements Agente {

    @Override
    public RespostaAgente responder(Contexto contexto) {
        return RespostaAgente.passar();
    }
}
