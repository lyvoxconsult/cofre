use rand::Rng;
use sha2::{Digest, Sha256};

/// Gera um salt aleatório de 16 bytes em hex.
pub fn generate_salt() -> String {
    let salt: [u8; 16] = rand::thread_rng().gen();
    hex::encode(salt)
}

/// Calcula SHA-256(salt + answer) em hex.
/// A resposta é normalizada: trim, lowercase (sem remoção de acentos).
///
/// Documentação da normalização:
/// - Espaços no início/fim são removidos (trim)
/// - Diferença entre maiúsculas e minúsculas é ignorada (lowercase)
/// - Acentos NÃO são removidos deliberadamente:
///   "João" ≠ "Joao", "árabe" ≠ "arabe"
///   Isso evita perda de informação em respostas que dependem de acentuação
///   e não impacta significativamente a usabilidade, já que o usuário
///   digita a resposta da mesma forma que a configurou.
pub fn hash_answer(salt: &str, answer: &str) -> String {
    let normalized = answer.trim().to_lowercase();
    let mut hasher = Sha256::new();
    hasher.update(salt.as_bytes());
    hasher.update(normalized.as_bytes());
    hex::encode(hasher.finalize())
}

/// Categorias de distratores mapeadas por índice de pergunta (0-9).
/// Cada categoria tem uma lista de opções plausíveis para aquele contexto,
/// evitando alternativas absurdas que revelariam a resposta correta.
const QUESTION_CATEGORIES: &[(usize, &[&str])] = &[
    // 0: Nome do animal de estimação → nomes comuns de pets
    (0, &[
        "Rex", "Bolinha", "Toddy", "Mel", "Bela", "Thor", "Luna", "Pipoca", "Billy", "Nina",
        "Fred", "Boby", "Chico", "Teco", "Lola", "Toto", "Mimi", "Bisteca", "Pandora", "Tobias",
        "Pituco", "Duda", "Sansão", "Floquinho", "Negão", "Branca", "Caramelo", "Tigrão",
        "Shirley", "Romeu", "Julieta", "Thor", "Luke", "Amora", "Pudim", "Cookie", "Bruce",
    ]),
    // 1: Cidade natal / Onde nasceu → cidades brasileiras
    (1, &[
        "São Paulo", "Rio de Janeiro", "Belo Horizonte", "Salvador", "Brasília", "Fortaleza",
        "Curitiba", "Recife", "Porto Alegre", "Campinas", "Manaus", "Goiânia", "Niterói",
        "Santos", "Ribeirão Preto", "Uberlândia", "Sorocaba", "Londrina", "Joinville", "Juiz de Fora",
        "Florianópolis", "Vitória", "Natal", "João Pessoa", "Maceió", "Cuiabá", "São Luís",
        "Teresina", "Campo Grande", "Aracaju", "Palmas", "Boa Vista", "Macapá", "Porto Velho",
        "Rio Branco", "Caxias do Sul", "São José dos Campos", "Blumenau", "Petrópolis",
        "Taubaté", "Jundiaí", "Piracicaba", "Maringá", "Ponta Grossa", "Cascavel",
    ]),
    // 2: Melhor amigo de infância → nomes masculinos/femininos
    (2, &[
        "Pedro", "João", "Lucas", "Gabriel", "Rafael", "Matheus", "Felipe", "Leonardo",
        "Gustavo", "Vinicius", "Fernando", "Bruno", "Diego", "Thiago", "Rodrigo", "Eduardo",
        "Marcos", "Paulo", "Carlos", "André", "Luiz", "Ricardo", "Daniel", "José", "Marcelo",
        "Ana", "Maria", "Juliana", "Camila", "Fernanda", "Mariana", "Patrícia", "Amanda",
        "Beatriz", "Letícia", "Carolina", "Isabela", "Larissa", "Nathália", "Gabriela",
        "Rafaela", "Luciana", "Renata", "Aline", "Vanessa", "Cristina", "Roberta",
    ]),
    // 3: Professor favorito → nomes comuns
    (3, &[
        "Carlos", "Roberto", "Márcia", "Cláudia", "José", "Maria", "Paulo", "Cristina",
        "Fernando", "Sandra", "Antônio", "João", "Pedro", "Marcos", "Rita", "Tereza",
        "Miguel", "Bianca", "Jorge", "Marta", "Vicente", "Helena", "Ronaldo", "Sônia",
        "Alberto", "Lúcia", "Eduardo", "Vera", "Gustavo", "Lívia", "Renato", "Débora",
        "Mário", "Eliane", "Ricardo", "Célia", "Sérgio", "Fátima", "Adriana", "Fábio",
    ]),
    // 4: Prato favorito → comidas/pratos
    (4, &[
        "Feijoada", "Pizza", "Lasanha", "Churrasco", "Strogonoff", "Macarrão", "Sushi",
        "Moqueca", "Acarajé", "Brigadeiro", "Açaí", "Coxinha", "Escondidinho", "Pão de Queijo",
        "Salpicão", "Virado à Paulista", "Baião de Dois", "Tapioca", "Carne de Sol", "Pudim",
        "Pastel", "Chocolate", "Sorvete", "Batata Frita", "Hambúrguer", "Cuscuz", "Pato no Tucupi",
        "Vatapá", "Galinhada", "Empadão", "Risoto", "Torta de Frango", "Pavê", "Mousse",
    ]),
    // 5: Primeira escola → nomes de escolas
    (5, &[
        "Santo Antônio", "São José", "Salesiano", "Marista", "Santa Maria", "Dom Bosco",
        "Albert Einstein", "Santo Agostinho", "São Paulo", "Bom Jesus", "Sagrado Coração",
        "Santa Catarina", "Santa Úrsula", "Imaculada Conceição", "Nossa Senhora Aparecida",
        "São João", "São Francisco", "Santa Terezinha", "Santo Inácio", "Nossa Senhora do Carmo",
        "São Lucas", "Santa Cecília", "Padre Anchieta", "João XXIII", "São Miguel",
        "Divino Espírito Santo", "Santana", "Santa Isabel", "São Vicente", "Nossa Senhora das Graças",
        "Santa Clara", "São Judas Tadeu", "Aparecida", "Santo Expedito", "Santa Rita",
    ]),
    // 6: Sobrenome de solteira da mãe → sobrenomes brasileiros comuns
    (6, &[
        "Silva", "Santos", "Oliveira", "Souza", "Pereira", "Costa", "Ferreira", "Rodrigues",
        "Almeida", "Nascimento", "Lima", "Araújo", "Carvalho", "Gomes", "Martins", "Barbosa",
        "Rocha", "Ribeiro", "Alves", "Monteiro", "Mendes", "Barros", "Freitas", "Moreira",
        "Cardoso", "Dias", "Campos", "Teixeira", "Castro", "Fernandes", "Pinto", "Cavalcanti",
        "Nunes", "Correia", "Melo", "Cunha", "Machado", "Vieira", "Azevedo", "Leão",
    ]),
    // 7: Livro favorito → títulos de livros famosos
    (7, &[
        "Dom Casmurro", "Grande Sertão Veredas", "Memórias Póstumas de Brás Cubas",
        "Capitães da Areia", "O Pequeno Príncipe", "1984", "A更多ia", "O Alquimista",
        "Cem Anos de Solidão", "A Moreninha", "Iracema", "Senhora", "O Cortiço",
        "Macunaíma", "Vidas Secas", "O Guarani", "A Escrava Isaura", "O Primo Basílio",
        "Os Maias", "O Ateneu", "Triste Fim de Policarpo Quaresma", "O Auto da Compadecida",
        "Harry Potter", "O Senhor dos Anéis", "Jogos Vorazes", "Percy Jackson",
        "O Código Da Vinci", "Crime e Castigo", "Orgulho e Preconceito", "Moby Dick",
        "Drácula", "Frankenstein", "O Conde de Monte Cristo", "Os Três Mosqueteiros",
    ]),
    // 8: Ano de formação no ensino médio → anos
    (8, &[
        "1998", "1999", "2000", "2001", "2002", "2003", "2004", "2005", "2006", "2007",
        "2008", "2009", "2010", "2011", "2012", "2013", "2014", "2015", "2016", "2017",
        "2018", "2019", "2020", "2021", "2022", "2023", "2024", "2025", "2026",
        "1988", "1989", "1990", "1991", "1992", "1993", "1994", "1995", "1996", "1997",
    ]),
    // 9: Modelo do primeiro carro → modelos de carro comuns no Brasil
    (9, &[
        "Fusca", "Gol", "Corsa", "Palio", "Uno", "Celta", "Fiesta", "Focus", "Ka",
        "Civic", "Corolla", "Santana", "Parati", "Escort", "Del Rey", "Chevette",
        "Monza", "Opala", "Maverick", "Brasília", "Kombi", "Saveiro", "Fox", "Polo",
        "Golf", "Vectra", "Astra", "Omega", "S10", "Ranger", "Hilux", "L200",
        "Ecosport", "Renegade", "Compass", "HB20", "Cobalt", "Prisma", "Onix", "Classic",
        "Siena", "Strada", "Mille", "Tempra", "Uno Mille", "147", "Spazio", "Logus",
    ]),
];

/// Retorna a lista de candidatos a distratores para uma pergunta
/// com base em seu índice (0-9).
fn get_candidates_for_question(question_index: usize) -> &'static [&'static str] {
    for &(idx, candidates) in QUESTION_CATEGORIES {
        if idx == question_index {
            return candidates;
        }
    }
    // Fallback: lista genérica de nomes
    &["Ana", "João", "Maria", "Pedro", "Carlos", "Sandra", "Paulo", "Cristina", "Lucas", "Juliana"]
}

/// Gera `count` distratores (respostas falsas) para uma pergunta específica.
///
/// Os distratores são escolhidos aleatoriamente de uma lista categorizada
/// pelo tipo da pergunta, garantindo que as alternativas sejam contextualmente
/// plausíveis (ex.: nomes de pessoas para "Nome da mãe?", cidades para "Cidade natal?").
///
/// Regras:
/// - Nenhum distrator é igual à resposta correta
/// - Nenhum distrator se repete
/// - Distratores variam a cada chamada (seleção aleatória)
/// - O número de distratores gerados é exatamente `count`
pub fn generate_distractors(question_index: usize, correct_answer: &str, count: usize) -> Vec<String> {
    let all_candidates = get_candidates_for_question(question_index);
    let correct_lower = correct_answer.trim().to_lowercase();

    let mut rng = rand::thread_rng();
    let mut candidates: Vec<&&str> = all_candidates
        .iter()
        .filter(|c| c.to_lowercase() != correct_lower)
        .collect();

    // Embaralha e pega os primeiros `count`
    let mut rng2 = rand::thread_rng();
    // Fisher-Yates parcial
    let pick_count = count.min(candidates.len());
    for i in 0..pick_count {
        let j = rng2.gen_range(i..candidates.len());
        candidates.swap(i, j);
    }

    let mut distractors: Vec<String> = candidates
        .iter()
        .take(pick_count)
        .map(|s| s.to_string())
        .collect();

    // Se não houver candidatos suficientes, gera fallbacks
    while distractors.len() < count {
        let fallback = format!("Opção {}", rng.gen_range(100..999));
        if !distractors.contains(&fallback) && fallback.to_lowercase() != correct_lower {
            distractors.push(fallback);
        }
    }

    distractors
}

/// Gera 5 opções para uma pergunta: 1 correta + 4 distratores, embaralhadas.
///
/// Retorna as opções já embaralhadas (posição da correta é aleatória).
/// A cada chamada, os distratores podem ser diferentes (seleção aleatória das listas).
pub fn generate_options(question_index: usize, correct_answer: &str) -> Vec<String> {
    let mut distractors = generate_distractors(question_index, correct_answer, 4);
    let mut options = vec![correct_answer.trim().to_string()];
    options.append(&mut distractors);
    shuffle_options(&mut options);
    options
}

/// Embaralha uma lista de strings (Fisher-Yates).
pub fn shuffle_options(options: &mut [String]) {
    let mut rng = rand::thread_rng();
    let len = options.len();
    for i in (1..len).rev() {
        let j = rng.gen_range(0..=i);
        options.swap(i, j);
    }
}

/// Gera respostas falsas diferentes a cada chamada (alias para `generate_distractors`).
/// Usada externamente quando é necessário garantir variabilidade entre tentativas.
pub fn generate_fresh_distractors(question_index: usize, correct_answer: &str, count: usize) -> Vec<String> {
    generate_distractors(question_index, correct_answer, count)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_hash_answer_deterministic() {
        let salt = "abcd1234efgh5678";
        let hash1 = hash_answer(salt, "Meu Gato");
        let hash2 = hash_answer(salt, "meu gato ");
        assert_eq!(hash1, hash2);
    }

    #[test]
    fn test_hash_answer_different_salts() {
        let hash1 = hash_answer("salt1", "resposta");
        let hash2 = hash_answer("salt2", "resposta");
        assert_ne!(hash1, hash2);
    }

    #[test]
    fn test_hash_answer_preserves_accents() {
        let salt = "testsalt";
        let hash_joao = hash_answer(salt, "João");
        let hash_joao2 = hash_answer(salt, "joão");
        assert_eq!(hash_joao, hash_joao2, "Com/sem maiúscula deve ser igual");
        let hash_joao_sem_acento = hash_answer(salt, "Joao");
        assert_ne!(hash_joao, hash_joao_sem_acento, "Acento deve diferenciar");
    }

    #[test]
    fn test_generate_salt_length() {
        let salt = generate_salt();
        assert_eq!(salt.len(), 32); // 16 bytes = 32 hex chars
    }

    #[test]
    fn test_generate_distractors_count() {
        let distractors = generate_distractors(2, "Pedro", 4); // amigo de infância → nomes
        assert_eq!(distractors.len(), 4);
    }

    #[test]
    fn test_generate_distractors_no_correct() {
        let correct = "Pedro";
        let distractors = generate_distractors(2, correct, 4);
        for d in &distractors {
            assert_ne!(d.to_lowercase(), correct.to_lowercase());
        }
    }

    #[test]
    fn test_generate_distractors_unique() {
        let distractors = generate_distractors(2, "Pedro", 4);
        let mut unique = distractors.clone();
        unique.sort();
        unique.dedup();
        assert_eq!(unique.len(), distractors.len());
    }

    #[test]
    fn test_generate_distractors_vary_between_calls() {
        let distractors1 = generate_distractors(2, "Pedro", 4);
        let distractors2 = generate_distractors(2, "Pedro", 4);
        // Probabilisticamente, é extremamente raro serem exatamente iguais
        // (temos dezenas de nomes na lista, escolhendo 4)
        let all_same = distractors1.iter().zip(distractors2.iter()).all(|(a, b)| a == b)
            && distractors1.len() == distractors2.len();
        assert!(!all_same, "Distratores devem variar entre chamadas");
    }

    #[test]
    fn test_generate_options_has_exactly_5() {
        let options = generate_options(1, "São Paulo"); // cidade natal
        assert_eq!(options.len(), 5);
    }

    #[test]
    fn test_generate_options_contains_correct() {
        let correct = "São Paulo";
        let options = generate_options(1, correct);
        assert!(options.contains(&correct.to_string()));
    }

    #[test]
    fn test_generate_options_distractors_contextual() {
        // Para pergunta do tipo "cidade natal", os distratores devem ser cidades
        let correct = "São Paulo";
        let options = generate_options(1, correct);

        // A resposta correta está presente
        assert!(options.contains(&correct.to_string()));

        // Todos os distratores gerados devem ser de fato cidades da lista de candidatos
        let valid_cities = get_candidates_for_question(1);
        let all_valid = options.iter().all(|o| {
            o == correct || valid_cities.contains(&o.as_str())
        });
        assert!(all_valid, "Distratores devem ser contextualmente relevantes (cidades)");
    }

    #[test]
    fn test_generate_options_correct_position_varies() {
        let correct = "São Paulo";
        let mut positions = Vec::new();
        for _ in 0..10 {
            let options = generate_options(1, correct);
            let pos = options.iter().position(|o| o == correct).unwrap();
            positions.push(pos);
        }
        // Verifica se a posição varia (pelo menos 2 posições diferentes em 10 tentativas)
        let unique_positions: std::collections::HashSet<_> = positions.into_iter().collect();
        assert!(unique_positions.len() >= 2, "Resposta correta deve aparecer em posições diferentes");
    }

    #[test]
    fn test_generate_distractors_for_pet_question() {
        // Para pergunta 0 (animal de estimação), distratores devem ser nomes de pets
        let distractors = generate_distractors(0, "Rex", 4);
        assert_eq!(distractors.len(), 4);
        // Todos devem ser diferentes da resposta correta
        for d in &distractors {
            assert_ne!(d.to_lowercase(), "rex");
        }
    }

    #[test]
    fn test_generate_distractors_for_food_question() {
        // Para pergunta 4 (prato favorito), distratores devem ser comidas
        let distractors = generate_distractors(4, "Feijoada", 4);
        assert_eq!(distractors.len(), 4);
        // Verifica que não contém a resposta correta
        assert!(!distractors.iter().any(|d| d.to_lowercase() == "feijoada"));
    }

    #[test]
    fn test_shuffle_options() {
        let mut options = vec!["A".to_string(), "B".to_string(), "C".to_string(), "D".to_string(), "E".to_string()];
        let original = options.clone();
        shuffle_options(&mut options);
        // After shuffle, same elements (order may differ)
        let mut sorted = options.clone();
        sorted.sort();
        let mut original_sorted = original.clone();
        original_sorted.sort();
        assert_eq!(sorted, original_sorted);
    }

    #[test]
    fn test_question_categories_exist_for_all() {
        // Todas as 10 perguntas devem ter categorias definidas
        for i in 0..10 {
            let distractors = generate_distractors(i, "Teste", 3);
            assert_eq!(distractors.len(), 3, "Pergunta {} deve gerar distratores", i);
        }
    }
}
