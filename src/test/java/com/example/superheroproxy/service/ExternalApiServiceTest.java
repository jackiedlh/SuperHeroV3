package com.example.superheroproxy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import com.example.superheroproxy.proto.Hero;
import com.example.superheroproxy.proto.SearchResponse;
import com.example.superheroproxy.utils.ResponseGenerator;

@ExtendWith(MockitoExtension.class)
public class ExternalApiServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ExternalApiService externalApiService;

    private static final String API_TOKEN = "test-token";
    private static final String BASE_URL = "https://api.example.com";
    private static final String HERO_ID = "123";
    private static final String HERO_NAME = "Superman";

    @BeforeEach
    void setUp() {
        externalApiService = new ExternalApiService(restTemplate);
        // Using reflection to set the private fields since they're injected via @Value
        try {
            var tokenField = ExternalApiService.class.getDeclaredField("apiToken");
            tokenField.setAccessible(true);
            tokenField.set(externalApiService, API_TOKEN);

            var urlField = ExternalApiService.class.getDeclaredField("baseUrl");
            urlField.setAccessible(true);
            urlField.set(externalApiService, BASE_URL);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set up test", e);
        }
    }

    @Test
    void getHero_shouldReturnHero_whenApiCallSucceeds() throws Exception {
        // Arrange
        String expectedUrl = String.format("%s/%s/%s", BASE_URL.trim(), API_TOKEN.trim(), HERO_ID);
        String mockJsonResponse = "{\"id\":\"123\",\"name\":\"Superman\",\"powerstats\":{\"intelligence\":\"88\",\"strength\":\"100\",\"speed\":\"100\",\"durability\":\"100\",\"power\":\"100\",\"combat\":\"85\"},\"biography\":{\"full-name\":\"Clark Kent\",\"alter-egos\":\"No alter egos found.\",\"aliases\":[\"Man of Steel\",\"The Man of Tomorrow\"],\"place-of-birth\":\"Krypton\",\"first-appearance\":\"Action Comics #1\",\"publisher\":\"DC Comics\",\"alignment\":\"good\"},\"appearance\":{\"gender\":\"Male\",\"race\":\"Kryptonian\",\"height\":[\"6'3\",\"191 cm\"],\"weight\":[\"225 lb\",\"101 kg\"],\"eye-color\":\"Blue\",\"hair-color\":\"Black\"},\"work\":{\"occupation\":\"Reporter\",\"base\":\"Metropolis\"},\"connections\":{\"group-affiliation\":\"Justice League of America, The Legion of Super-Heroes (pre-Crisis as Superboy)\",\"relatives\":\"Lois Lane (wife)\"},\"image\":{\"url\":\"https://www.superherodb.com/pictures2/portraits/10/100/791.jpg\"}}";
        Hero expectedHero = Hero.newBuilder()
            .setId("123")
            .setName("Superman")
            .build();

        when(restTemplate.getForObject(eq(expectedUrl), eq(String.class)))
            .thenReturn(mockJsonResponse);

        try (MockedStatic<ResponseGenerator> mockedStatic = mockStatic(ResponseGenerator.class)) {
            mockedStatic.when(() -> ResponseGenerator.generateHero(mockJsonResponse))
                .thenReturn(expectedHero);

            // Act
            Hero result = externalApiService.getHero(HERO_ID);

            // Assert
            assertNotNull(result);
            assertEquals(expectedHero.getId(), result.getId());
            assertEquals(expectedHero.getName(), result.getName());
            verify(restTemplate).getForObject(eq(expectedUrl), eq(String.class));
            mockedStatic.verify(() -> ResponseGenerator.generateHero(mockJsonResponse));
        }
    }

    @Test
    void getHero_shouldThrowException_whenApiCallFails() {
        // Arrange
        String expectedUrl = String.format("%s/%s/%s", BASE_URL.trim(), API_TOKEN.trim(), HERO_ID);
        when(restTemplate.getForObject(eq(expectedUrl), eq(String.class)))
            .thenThrow(new RuntimeException("API Error"));

        // Act & Assert
        assertThrows(Exception.class, () -> externalApiService.getHero(HERO_ID));
    }

    @Test
    void searchHero_shouldReturnSearchResponse_whenApiCallSucceeds() throws Exception {
        // Arrange
        String expectedUrl = String.format("%s/%s/search/%s", BASE_URL.trim(), API_TOKEN.trim(), HERO_NAME);
        String mockJsonResponse = "{\"response\":\"success\",\"results\":[{\"id\":\"123\",\"name\":\"Superman\",\"powerstats\":{\"intelligence\":\"88\",\"strength\":\"100\",\"speed\":\"100\",\"durability\":\"100\",\"power\":\"100\",\"combat\":\"85\"},\"biography\":{\"full-name\":\"Clark Kent\",\"alter-egos\":\"No alter egos found.\",\"aliases\":[\"Man of Steel\",\"The Man of Tomorrow\"],\"place-of-birth\":\"Krypton\",\"first-appearance\":\"Action Comics #1\",\"publisher\":\"DC Comics\",\"alignment\":\"good\"},\"appearance\":{\"gender\":\"Male\",\"race\":\"Kryptonian\",\"height\":[\"6'3\",\"191 cm\"],\"weight\":[\"225 lb\",\"101 kg\"],\"eye-color\":\"Blue\",\"hair-color\":\"Black\"},\"work\":{\"occupation\":\"Reporter\",\"base\":\"Metropolis\"},\"connections\":{\"group-affiliation\":\"Justice League of America, The Legion of Super-Heroes (pre-Crisis as Superboy)\",\"relatives\":\"Lois Lane (wife)\"},\"image\":{\"url\":\"https://www.superherodb.com/pictures2/portraits/10/100/791.jpg\"}}]}";
        SearchResponse expectedResponse = SearchResponse.newBuilder()
            .setResponse("success")
            .setResultsFor(HERO_NAME)
            .addResults(Hero.newBuilder().setId("123").setName("Superman").build())
            .setTotalCount(1)
            .setCurrentPage(1)
            .setTotalPages(1)
            .build();

        when(restTemplate.getForObject(eq(expectedUrl), eq(String.class)))
            .thenReturn(mockJsonResponse);

        try (MockedStatic<ResponseGenerator> mockedStatic = mockStatic(ResponseGenerator.class)) {
            mockedStatic.when(() -> ResponseGenerator.createSearchResponse(HERO_NAME, mockJsonResponse))
                .thenReturn(expectedResponse);

            // Act
            SearchResponse result = externalApiService.searchHero(HERO_NAME);

            // Assert
            assertNotNull(result);
            assertEquals(expectedResponse.getResponse(), result.getResponse());
            assertEquals(expectedResponse.getResultsFor(), result.getResultsFor());
            assertEquals(expectedResponse.getResultsCount(), result.getResultsCount());
            verify(restTemplate).getForObject(eq(expectedUrl), eq(String.class));
            mockedStatic.verify(() -> ResponseGenerator.createSearchResponse(HERO_NAME, mockJsonResponse));
        }
    }

    @Test
    void searchHero_shouldThrowException_whenApiCallFails() {
        // Arrange
        String expectedUrl = String.format("%s/%s/search/%s", BASE_URL.trim(), API_TOKEN.trim(), HERO_NAME);
        when(restTemplate.getForObject(eq(expectedUrl), eq(String.class)))
            .thenThrow(new RuntimeException("API Error"));

        // Act & Assert
        assertThrows(Exception.class, () -> externalApiService.searchHero(HERO_NAME));
    }
} 