package cloud.mindbox.mylibrary;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MindboxLib {

    public void execute() throws Exception {
//String urlString = "https://api.mindbox.ru/v3/operations/async?dateTimeOffset=0&operation=viewProduct&deviceUUID=9d70dc4f-52b4-42b7-9a1b-f9aefc2680ec&endpointId=Mpush-test.AndroidAppExample&transactionId=9196530b-195f-4272-a396-f11f9127e9a1";
//String jsonInputString = "{\"viewProduct\":{\"product\":{\"ids\":{\"website\":\"123\"}}}}";

        String urlString = "https://api.mindbox.ru/v3/operations/async?dateTimeOffset=0&operation=setProductList&deviceUUID=9d70dc4f-52b4-42b7-9a1b-f9aefc2680ec&endpointId=mpush-test-Android&transactionId=0f807f12-b78f-424d-8ad0-4fe2769bc217";
        String jsonInputString = "{\"productList\": [{\"count\": 10,\"pricePerItem\": 1,\"product\": {\"ids\": { \"website\": \"test-1\"}}},{\"count\": 2,\"pricePerItem\": 4,\"productGroup\": {\"ids\": { \"website\": \"test-group-1\"}}} ]";

        operation(urlString, jsonInputString);
    }

    private void operation(String urlString, String jsonInputString) throws Exception {

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

// Устанавливаем метод запроса на POST
        conn.setRequestMethod("POST");

// Устанавливаем заголовки
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");

// Если требуется авторизация, добавьте соответствующий заголовок
// conn.setRequestProperty("Authorization", "Bearer YOUR_ACCESS_TOKEN");

// Разрешаем отправку данных в запросе
        conn.setDoOutput(true);

// Тело запроса

// Отправляем тело запроса
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonInputString.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

// Читаем ответ от сервера
        int code = conn.getResponseCode();
        System.out.println("Response Code: " + code);

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            System.out.println("Response: " + response.toString());
        }
    }
}
