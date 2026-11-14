-- Dados de demonstracao do Verificador de Precos (MySQL).
-- Importe somente depois de criar o banco e executar: python manage.py migrate
-- Destinado a uma base de demonstracao vazia; execute uma unica vez.
-- As tabelas sao criadas pelas migrations do Django. Este arquivo nao altera a estrutura.
SET NAMES utf8mb4;

INSERT INTO `produtos` (`codigo_barra`, `nome`, `descricao`, `preco`, `data_atualizacao`)
VALUES
    ('7896277200136','Chá de Camomila Madrugada','Chá de Camomila Madrugada, CX 10 saquinhos',2.49,'2026-10-16 22:10:59.895561'),
    ('12345670','Teste á é @ ñ ; \' \" , . : ´ ` { } [ ] ( ) < > _ - | \\ / ! # $ %','Teste á é @ ñ ; \' \" , . : ´ ` { } [ ] ( ) < > _ - | \\ / ! # $ %',1.99,'2026-10-16 22:11:10.350879'),
    ('7896328210756','Chá de Camomila Gengibre e Mel Barão','Chá misto de Camomila Gengibre e Mel.\r\nBarão de Cotegipe.',2.99,'2026-10-16 22:19:58.127546'),
    ('7896079431158','Arroz branco Namorado 5kg Tipo 1','Arroz branco Namorado 5kg Tipo 1',24.99,'2026-11-03 23:00:30.087844'),
    ('7894900029628','Coca Zero açúcar Zero Cafeína','Coca-Cola Zero Cafeína, Zero açúcar.',6.59,'2026-11-09 22:07:41.223457');
