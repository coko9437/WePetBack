1단계: 비용 및 구독 (유료 구독은 필요한가?)
결론: 아니요, 프로젝트 개발 및 공모전 시연 목적으론 유료 구독이 필요 없습니다.
Google AI for Developers 무료 등급 (Free Tier): Google은 개발자들이 Gemini 
API를 테스트하고 애플리케이션을 빌드할 수 있도록 넉넉한 무료 사용량을 제공합니다.
Gemini Pro 모델 기준, 분당 60회 요청(RPM)까지 무료입니다.
학생 프로젝트나 초기 스타트업 수준에서는 이 무료 사용량을 초과할 일이 거의 없습니다.
유료 구독과의 차이점: 사용자들이 월 요금을 내고 사용하는 'Gemini Advanced'(Google One 구독)는 챗봇 서비스 자체를 더 많이, 더 빠르게 사용하기 위한 소비자용 상품입니다. 
우리가 사용할 것은 개발자를 위한 API 서비스로, 별개의 과금 체계를 가집니다.
따라서 비용 걱정 없이 편하게 개발을 시작하시면 됩니다.
2단계: API 키 발급받기
이것이 Gemini의 능력을 우리 서버에서 사용할 수 있게 해주는 '마법 열쇠'입니다.
Google AI Studio 접속: 웹 브라우저에서 Google AI Studio로 이동하고 구글 계정으로 로그인합니다.
API 키 생성:
왼쪽 메뉴에서 "Get API key" 탭을 클릭합니다.
"Create API key in new project" 버튼을 누릅니다.
버튼을 누르면 즉시 긴 문자열 형태의 API 키가 생성됩니다. 이 키를 안전한 곳에 복사해 두세요. (주의: 이 키는 비밀번호처럼 외부에 노출되면 안 됩니다!)
Spring Boot 프로젝트에 키 저장:
src/main/resources/application.properties 파일에 다음과 같이 키를 저장합니다. 절대 Java 코드 안에 직접 문자열로 넣지 마세요.
code
Properties
# application.properties
gemini.api.key=여기에_복사한_API_키를_붙여넣으세요
3단계: 이미지 분석을 위한 핵심 로직 및 프롬프트 설계
이제 Spring Boot 서버가 Gemini에게 어떻게 일을 시킬지 구체적으로 설계합니다.
A. 핵심 로직 흐름
React(프론트엔드)에서 사용자가 '실종 동물 사진 A'와 '보호 중인 동물 사진 B'를 업로드합니다.
Spring Boot 컨트롤러가 이 두 이미지 파일을 MultipartFile 형태로 받습니다.
서비스 계층(Service Layer)에서 받은 이미지 파일들을 Base64 문자열로 인코딩합니다.
왜? API 요청은 순수한 텍스트(JSON)로 이루어집니다. 이미지 파일 자체를 보낼 수 없으므로, 이미지를 텍스트 형태로 변환하는 표준 방식이 Base64 인코딩입니다.
미리 설계해 둔 **프롬프트(명령어)**와 Base64로 인코딩된 두 이미지 데이터를 조합하여 Gemini API에 보낼 **요청 본문(JSON)**을 만듭니다.
RestTemplate 또는 WebClient를 사용하여 Gemini API 엔드포인트로 이 JSON을 담아 POST 요청을 보냅니다.
Gemini API로부터 분석 결과가 담긴 JSON 응답을 받습니다.
이 응답을 파싱(해석)하여 프론트엔드에 전달하기 좋은 형태로 가공한 뒤, 최종 결과를 반환합니다.
B. 프롬프트 설계 (가장 중요한 부분!)
Gemini가 우리가 원하는 결과물을 정확히 만들어내도록 지시하는 '명령서'를 작성하는 과정입니다. 이 프롬프트의 품질이 결과물의 품질을 결정합니다.
[매우 효과적인 프롬프트 예시]
code
Code
You are a highly intelligent AI assistant specializing in finding lost pets.
Your task is to analyze and compare the two provided images.

Based on your analysis, provide the result ONLY in a valid JSON format. Do not add any other explanatory text before or after the JSON block.

The JSON object must contain the following fields:
- "similarityScore": An integer between 0 and 100 representing the likelihood that the animals in the two images are the same.
- "positiveFactors": An array of strings describing features that are similar between the two animals (e.g., "Similar white patch on the chest", "Same breed identified as Maltese").
- "negativeFactors": An array of strings describing features that are different or uncertain (e.g., "Eye color is hard to determine in the first image", "Apparent size difference, but could be due to camera angle").
- "finalVerdict": A brief, one-sentence conclusion in Korean.

Now, analyze the two images provided.
왜 이 프롬프트가 좋을까요?

역할 부여 (Persona): "실종 동물 찾기 전문가"라는 역할을 부여하여 AI의 답변 방향성을 설정합니다.
명확한 지시 (Task): 두 이미지를 분석하고 비교하라는 명확한 목표를 제시합니다.
출력 형식 강제 (Format): "ONLY in a valid JSON format" 라고 명시하여, 서버가 다루기 힘든 불필요한 문장("네, 분석해 드리겠습니다." 등)을 제외하고 깔끔한 데이터만 받도록 강제합니다.
상세한 스키마 정의 (Schema): JSON에 어떤 필드(similarityScore, positiveFactors 등)가 들어가야 하고, 각 필드의 데이터 타입(정수, 문자열 배열 등)이 무엇인지 명확하게 알려주어 일관된 결과를 얻게 합니다.
4단계: Spring Boot 실제 구현 (코드 예시)
A. API 요청을 보내기 위한 DTO(Data Transfer Object) 생성
Gemini API에 보낼 JSON 요청 본문과 응답을 Java 객체로 쉽게 다루기 위해 클래스를 만듭니다.
code
Java
// 요청 Body를 위한 클래스들
record GeminiRequest(List<Content> contents) {}
record Content(List<Part> parts) {}
// Part는 텍스트가 될 수도, 이미지가 될 수도 있습니다.
record Part(String text, InlineData inlineData) {
public static Part fromText(String text) {
return new Part(text, null);
}
public static Part fromImage(String mimeType, String base64Data) {
return new Part(null, new InlineData(mimeType, base64Data));
}
}
record InlineData(String mimeType, String data) {}

// 응답 Body를 파싱하기 위한 클래스들 (실제 응답 구조에 맞게 조정 필요)
record GeminiResponse(...) {}
B. 서비스 클래스 구현
code
Java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.util.Base64;
import java.util.List;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public String analyzeImages(MultipartFile imageA, MultipartFile imageB) throws IOException {
        // 1. API URL 및 프롬프트 정의
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro-vision:generateContent?key=" + apiKey;
        String prompt = "You are a highly intelligent AI assistant... (위에 작성한 프롬프트 전체)"; // 프롬프트 전체

        // 2. 이미지 파일 Base64 인코딩
        String base64ImageA = Base64.getEncoder().encodeToString(imageA.getBytes());
        String base64ImageB = Base64.getEncoder().encodeToString(imageB.getBytes());

        // 3. API 요청 본문(JSON) 생성
        List<Part> parts = List.of(
                Part.fromText(prompt),
                Part.fromImage(imageA.getContentType(), base64ImageA),
                Part.fromImage(imageB.getContentType(), base64ImageB)
        );
        GeminiRequest requestBody = new GeminiRequest(List.of(new Content(parts)));

        // 4. API 호출 및 응답 받기
        // 실제로는 HttpEntity를 만들고 헤더를 설정하는 것이 더 좋습니다.
        GeminiResponse response = restTemplate.postForObject(url, requestBody, GeminiResponse.class);

        // 5. 응답에서 필요한 텍스트(우리가 요청한 JSON) 추출
        // response 객체 구조를 파싱해서 최종 텍스트를 추출해야 합니다.
        // String resultJson = response.getCandidates().get(0).getContent().getParts().get(0).getText();
        
        // return resultJson; // 이 결과가 프론트엔드로 전달될 JSON 문자열입니다.
        
        // 임시 반환 값
        return "API 호출 후, Gemini가 생성한 JSON 응답이 여기에 담깁니다.";
    }
}
5단계: 결과 도출 및 활용
위 analyzeImages 메서드를 실행하면, Gemini는 우리가 설계한 프롬프트에 따라 다음과 같은 JSON 형식의 문자열을 반환할 것입니다.
[예상 결과물]
code
JSON
{
"similarityScore": 85,
"positiveFactors": [
"Both appear to be a brown Poodle mix.",
"A distinctive black spot is visible above the left eye in both images.",
"Similar body size and curly fur texture."
],
"negativeFactors": [
"The collar in the first image is red, but it's blue in the second.",
"The lighting conditions are different, making a perfect fur color comparison difficult."
],
"finalVerdict": "목걸이 색은 다르지만, 특이한 반점과 품종이 일치하여 동일한 동물일 가능성이 매우 높습니다."
}
이 결과 문자열을 받은 Spring Boot 컨트롤러는 그대로 React 프론트엔드로 전달합니다. 그러면 프론트엔드에서는 이 데이터를 사용하여 다음과 같은 UI를 사용자에게 보여줄 수 있습니다.
이 아이가 아닐까요? (유사도: 85%)
✔️ 비슷한 점
두 동물 모두 갈색 푸들 믹스로 보입니다.
왼쪽 눈 위에 뚜렷한 검은 점이 동일하게 보입니다.
⚠️ 다른/불확실한 점
첫 번째 사진의 목걸이는 빨간색이지만, 두 번째 사진에서는 파란색입니다.
💬 최종 분석
목걸이 색은 다르지만, 특이한 반점과 품종이 일치하여 동일한 동물일 가능성이 매우 높습니다.