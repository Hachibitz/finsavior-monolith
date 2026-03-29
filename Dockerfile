# Usa a imagem oficial do Amazon Corretto 21 (Alpine para ser mais leve)
FROM amazoncorretto:21-alpine

# Define o diretório de trabalho dentro do contêiner
WORKDIR /app

# Copia o JAR gerado pelo Gradle para dentro da imagem Docker
# (O * serve para pegar o nome dinâmico da versão do seu app)
COPY build/libs/*-SNAPSHOT.jar app.jar

# Define o fuso horário para o Brasil (Importante para os logs)
ENV TZ=America/Sao_Paulo

# Comando que sobe a aplicação
ENTRYPOINT ["java", "-jar", "app.jar"]
