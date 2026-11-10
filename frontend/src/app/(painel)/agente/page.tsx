"use client";

import { ChevronDown, Loader2, Save, Sparkles } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import useSWR from "swr";
import { ErroComRetentativa } from "@/components/painel/erro-com-retentativa";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { buscar, gerarEstilo, salvarPrompt, type Prompt } from "@/lib/api";

const LIMITE = 8000;

export default function Agente() {
  const { data, error, mutate } = useSWR<Prompt>("/api/prompt", buscar, { revalidateOnFocus: false });
  // Rascunho = o que o corretor digitou; null = ainda não mexeu, vale o que está salvo.
  const [rascunho, setRascunho] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);
  const [gerando, setGerando] = useState(false);

  const texto = rascunho ?? data?.base ?? "";
  const alterado = data !== undefined && texto !== data.base;

  async function salvar() {
    setSalvando(true);
    try {
      await mutate(await salvarPrompt(texto), { revalidate: false });
      setRascunho(null);
      toast.success("Instruções salvas. Valem para as próximas respostas.");
    } catch {
      toast.error("Não foi possível salvar. Tente de novo.");
    } finally {
      setSalvando(false);
    }
  }

  async function gerar() {
    setGerando(true);
    try {
      const { gerado } = await gerarEstilo();
      if (gerado) {
        await mutate();
        toast.success("Estilo atualizado a partir das suas mensagens.");
      } else {
        toast.info("Ainda não há mensagens suas suficientes. São necessárias pelo menos 20.");
      }
    } catch {
      toast.error("Não foi possível gerar o estilo agora.");
    } finally {
      setGerando(false);
    }
  }

  return (
    <div className="h-full overflow-y-auto">
      <div className="mx-auto flex max-w-2xl flex-col gap-6 p-4 md:p-8">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Agente</h1>
          <p className="text-sm text-muted-foreground">Como a IA deve atender os seus clientes.</p>
        </div>

        {error ? (
          <ErroComRetentativa
            titulo="Não consegui carregar as instruções"
            descricao="Tente de novo em instantes."
            aoTentarDeNovo={() => mutate()}
          />
        ) : !data ? (
          <Skeleton className="h-96 w-full rounded-xl" />
        ) : (
          <>
            <Card>
              <CardHeader>
                <CardTitle>Instruções</CardTitle>
                <CardDescription>
                  Escreva do seu jeito: quem você é, como atende, o que pode prometer e quando chamar você.
                </CardDescription>
              </CardHeader>
              <CardContent>
                <Textarea
                  value={texto}
                  onChange={(e) => setRascunho(e.target.value)}
                  maxLength={LIMITE}
                  rows={12}
                  aria-label="Instruções do agente"
                  className="resize-y"
                />
                <p className="mt-2 text-right text-xs text-muted-foreground">
                  {texto.length} / {LIMITE}
                </p>
              </CardContent>
              <CardFooter className="justify-end">
                <Button onClick={salvar} disabled={!alterado || salvando}>
                  {salvando ? <Loader2 className="animate-spin" /> : <Save />}
                  Salvar
                </Button>
              </CardFooter>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Estilo de escrita</CardTitle>
                <CardDescription>
                  A IA aprende o seu tom lendo mensagens que você já enviou. Quando há estilo, ele vale mais que o
                  tom formal padrão.
                </CardDescription>
              </CardHeader>
              <CardContent>
                {data.estilo ? (
                  <p className="whitespace-pre-wrap rounded-lg bg-muted p-3 text-sm">{data.estilo}</p>
                ) : (
                  <p className="text-sm text-muted-foreground">Ainda sem estilo. A IA usa um tom formal.</p>
                )}
              </CardContent>
              <CardFooter className="justify-end">
                <Button variant="outline" onClick={gerar} disabled={gerando}>
                  {gerando ? <Loader2 className="animate-spin" /> : <Sparkles />}
                  Gerar a partir das minhas mensagens
                </Button>
              </CardFooter>
            </Card>

            <Collapsible>
              <CollapsibleTrigger className="group flex w-full items-center justify-between rounded-lg border px-4 py-3 text-left text-sm font-medium hover:bg-muted">
                Regras fixas
                <ChevronDown className="size-4 transition-transform group-data-[state=open]:rotate-180" aria-hidden />
              </CollapsibleTrigger>
              <CollapsibleContent>
                <p className="px-4 pt-3 text-xs text-muted-foreground">
                  Estas regras sempre valem e não podem ser editadas.
                </p>
                <p className="whitespace-pre-wrap px-4 py-3 text-sm">{data.regrasFixas}</p>
              </CollapsibleContent>
            </Collapsible>
          </>
        )}
      </div>
    </div>
  );
}
