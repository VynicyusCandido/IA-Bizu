"use client";

import { CircleCheck } from "lucide-react";
import useSWR from "swr";
import { ErroComRetentativa } from "@/components/painel/erro-com-retentativa";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { conectarWhatsapp, type SituacaoWhatsapp } from "@/lib/api";

const PASSOS = [
  "Abra o WhatsApp no celular.",
  "Toque em Aparelhos conectados e depois em Conectar um aparelho.",
  "Aponte a câmera para o código ao lado.",
];

export default function Conexao() {
  // POST de propósito: cria a instância na primeira vez e pede o QR. A barra do painel só consulta (GET).
  const { data, error, mutate } = useSWR<SituacaoWhatsapp>("conectar-whatsapp", conectarWhatsapp, { refreshInterval: 5000 });

  return (
    <div className="h-full overflow-y-auto">
      <div className="mx-auto flex max-w-2xl flex-col gap-6 p-4 md:p-8">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Conexão</h1>
          <p className="text-sm text-muted-foreground">Ligue o seu número de WhatsApp ao Bizu.</p>
        </div>

        {error ? (
          <ErroComRetentativa
            titulo="Não consegui consultar o WhatsApp"
            descricao="Confira se a Evolution API está rodando e tente de novo."
            aoTentarDeNovo={() => mutate()}
          />
        ) : !data ? (
          <Skeleton className="h-72 w-full rounded-xl" />
        ) : data.conectado ? (
          <Card>
            <CardHeader>
              <div className="flex items-center gap-3">
                <CircleCheck className="size-6 text-emerald-500" aria-hidden />
                <div>
                  <CardTitle>WhatsApp conectado</CardTitle>
                  <CardDescription>A IA já pode atender os seus clientes novos.</CardDescription>
                </div>
              </div>
            </CardHeader>
          </Card>
        ) : (
          <Card>
            <CardHeader>
              <CardTitle>Leia o código para conectar</CardTitle>
              <CardDescription>O código muda de tempos em tempos; esta tela atualiza sozinha.</CardDescription>
            </CardHeader>
            <CardContent className="grid gap-6 sm:grid-cols-[auto_1fr] sm:items-center">
              {data.qrCodeBase64 ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={data.qrCodeBase64.startsWith("data:") ? data.qrCodeBase64 : `data:image/png;base64,${data.qrCodeBase64}`}
                  alt="QR code para conectar o WhatsApp"
                  className="mx-auto size-56 rounded-lg border bg-white p-2"
                />
              ) : (
                <Skeleton className="mx-auto size-56 rounded-lg" />
              )}
              <div className="flex flex-col gap-4">
                <ol className="flex flex-col gap-2 text-sm">
                  {PASSOS.map((passo, i) => (
                    <li key={passo} className="flex gap-3">
                      <span className="grid size-5 shrink-0 place-items-center rounded-full bg-muted text-xs font-medium">
                        {i + 1}
                      </span>
                      {passo}
                    </li>
                  ))}
                </ol>
                {data.codigoDeEmparelhamento && (
                  <p className="text-sm text-muted-foreground">
                    Sem câmera? Use o código{" "}
                    <span className="font-mono font-medium text-foreground">{data.codigoDeEmparelhamento}</span>.
                  </p>
                )}
              </div>
            </CardContent>
          </Card>
        )}
      </div>
    </div>
  );
}
