# GeoCerca V1.2 — Cercamento com arame farpado

Aplicativo Android para importar o perímetro de um terreno, desenhar o cercamento, estimar materiais e gerar orçamento de mão de obra em PDF.

## O que já está nesta V1.2

- Importação de KML com leitura automática dos vértices/polígono.
- Importação de TXT com pontos UTM.
- No TXT, ligação manual dos pontos na ordem desejada.
- Desenho do perímetro no app e no PDF.
- Distância de cada trecho e perímetro total.
- Mourões intermediários com espaçamento máximo configurável (padrão 3,00 m).
- Mourões de canto, mourões de travamento e escoras.
- Quantidade de fiadas configurável.
- Cálculo de arame farpado com margem de reserva.
- Cálculo de rolos de arame.
- Cálculo de grampos em unidades, kg e pacotes.
- Dados do cliente, propriedade e local.
- Dados do responsável pelo serviço: nome, telefone/WhatsApp e e-mail.
- Dados do responsável ficam salvos no aparelho para os próximos orçamentos.
- Orçamento de mão de obra por metro linear ou valor fechado.
- No modo por metro linear, o preço-base vale para até 4 fiadas.
- Fiadas acima de 4 recebem adicional por fiada extra por metro, configurável.
- Opção de ocultar a composição do preço e mostrar somente o total no PDF.
- Validade do orçamento, forma de pagamento e prazo de execução.
- PDF único com dados do projeto + croqui + lista de materiais + orçamento + condições.
- Compartilhamento do orçamento PDF pelo WhatsApp.
- Ícone GeoCerca aplicado ao app.

## Regra de orçamento por metro linear

Valor final por metro = valor-base (até 4 fiadas) + [(fiadas - 4) x adicional por fiada extra]

Exemplo: base R$ 20,00/m, 6 fiadas e adicional de R$ 2,50/m por fiada extra:

- 2 fiadas extras
- adicional total: R$ 5,00/m
- valor final: R$ 25,00/m

## Materiais

Os materiais são apenas estimados e especificados no orçamento. O fornecimento é de responsabilidade do cliente.
