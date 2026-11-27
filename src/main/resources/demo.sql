INSERT INTO authors(name) VALUES ('Machado de Assis'), ('Aluísio Azevedo'), ('Lima Barreto'),
    ('Maria Firmina dos Reis'), ('José de Alencar'), ('Júlia Lopes de Almeida'), ('Olavo Bilac')
ON CONFLICT DO NOTHING;

INSERT INTO subjects(name) VALUES ('Romance'), ('Poesia'), ('Sátira'), ('Literatura brasileira')
ON CONFLICT DO NOTHING;

INSERT INTO books(title,description,publication_year,author_id,subject_id)
SELECT item.title,item.description,item.year,a.id,s.id
FROM (VALUES
    ('Dom Casmurro', 'Bento Santiago revisita a amizade com Capitu e a vida que os dois construíram. A memória e o ciúme reorganizam o passado a cada capítulo.',1899,'Machado de Assis','Romance'),
    ('Memórias Póstumas de Brás Cubas','Um narrador que já morreu recompõe a própria trajetória com ironia e questiona as ambições da elite do século XIX.',1881,'Machado de Assis','Sátira'),
    ('O Cortiço','O cotidiano de uma habitação coletiva no Rio de Janeiro revela relações de poder, trabalho e convivência no fim do século XIX.',1890,'Aluísio Azevedo','Romance'),
    ('Triste Fim de Policarpo Quaresma','Um funcionário público idealista tenta colocar seu patriotismo em prática e se depara com a política do Brasil republicano.',1915,'Lima Barreto','Sátira'),
    ('Úrsula','Uma história de amor e perda que também dá voz a personagens escravizados e denuncia a violência da escravidão.',1859,'Maria Firmina dos Reis','Literatura brasileira'),
    ('Iracema','No Ceará do início da colonização, o encontro entre Iracema e Martim dá origem a uma narrativa sobre amor, pertencimento e perda.',1865,'José de Alencar','Romance'),
    ('A Falência','Uma família do Rio de Janeiro enfrenta a ruína financeira e vê expostas as tensões de classe e gênero da sociedade da época.',1901,'Júlia Lopes de Almeida','Literatura brasileira'),
    ('Poesias','A coletânea de Olavo Bilac trabalha a forma do verso com atenção ao ritmo, à rima e à escolha das palavras.',1888,'Olavo Bilac','Poesia')
) AS item(title,description,year,author,subject)
JOIN authors a ON a.name=item.author
JOIN subjects s ON s.name=item.subject
WHERE NOT EXISTS (SELECT 1 FROM books b WHERE b.title=item.title AND b.author_id=a.id)
ORDER BY item.title;

INSERT INTO copies(book_id)
SELECT b.id FROM books b
CROSS JOIN LATERAL generate_series(1, CASE b.title WHEN 'Úrsula' THEN 1 WHEN 'Dom Casmurro' THEN 4 ELSE 2 END)
WHERE b.title IN ('Dom Casmurro','Memórias Póstumas de Brás Cubas','O Cortiço','Triste Fim de Policarpo Quaresma',
                 'Úrsula','Iracema','A Falência','Poesias')
  AND NOT EXISTS (SELECT 1 FROM copies c WHERE c.book_id=b.id);

INSERT INTO students(ra,name,email,class_name) VALUES
    ('DEMO-001','Estudante de demonstração 1','demo1@example.invalid','3º A'),
    ('DEMO-002','Estudante de demonstração 2','demo2@example.invalid','2º B')
ON CONFLICT DO NOTHING;

INSERT INTO loans(student_id,copy_id,borrowed_at,due_date,notes)
SELECT s.id,c.id,d.today-12,d.today-2,'Registro fictício de demonstração.'
FROM students s
CROSS JOIN (SELECT c.id FROM copies c JOIN books b ON b.id=c.book_id WHERE b.title='Úrsula' ORDER BY c.id LIMIT 1) c
CROSS JOIN (SELECT (current_timestamp AT TIME ZONE 'America/Sao_Paulo')::date AS today) d
WHERE s.ra='DEMO-001'
ON CONFLICT DO NOTHING;
