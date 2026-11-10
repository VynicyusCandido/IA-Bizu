import { TriangleAlert } from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";

export function ErroComRetentativa({
  titulo,
  descricao,
  aoTentarDeNovo,
}: {
  titulo: string;
  descricao: string;
  aoTentarDeNovo: () => void;
}) {
  return (
    <Alert variant="destructive">
      <TriangleAlert />
      <AlertTitle>{titulo}</AlertTitle>
      <AlertDescription>
        <p>{descricao}</p>
        <Button variant="outline" size="sm" className="mt-2" onClick={aoTentarDeNovo}>
          Tentar de novo
        </Button>
      </AlertDescription>
    </Alert>
  );
}
