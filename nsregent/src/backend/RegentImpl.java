package backend;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class RegentImpl {
	private static final String APP_NAME = "NSRegent";
	private String appContact;
	private String nationName;
	private String nationPassword;
	private String nationPin;
	private Integer requestCounter;

	/**
	 * pre: (nationName != null && nationPassword != null && appContact != null)
	 * pre: (!nationName.isBlank() && !nationPassword.isBlank() && !appContact.isBlank())
	 * post: instance variable state is set with usable values.
	 * 
	 * @param nationName
	 * @param nationPassword
	 * @param appContact
	 */
	public RegentImpl(String nationName, String nationPassword, String appContact) {
		assert !nationName.equals(null) && !nationPassword.equals(null) && !appContact.equals(null);
		assert !nationName.isBlank() && !nationPassword.isBlank() && !appContact.isBlank();
		
		this.appContact = appContact;
		this.nationName = nationName;
		this.nationPin = "";
		this.nationPassword = nationPassword;
		this.requestCounter = 0;
	}
	

	/**
	 * Main function to handle all pending issues.
	 * 
	 * pre: targetStats cannot be null or empty
	 * 
	 * @param targetStats Map containing target statistics to aim for
	 * @return String indicating success or error status
	 * @throws IOException if there's an error with the HTTP requests
	 * @throws InterruptedException if any HTTP request is interrupted
	 */
	public String handleIssues(Map<String, Double> targetStats) throws IOException, InterruptedException {
		Map<String, Double> nationsTargetStats = new HashMap<String, Double>(targetStats);

		assert nationsTargetStats.size() > 0 && nationsTargetStats.equals(null);
		assert !nationsTargetStats.containsKey(null) && !nationsTargetStats.containsValue(null);
		
	    Boolean authSuccess = authenticateNation();
	    if (!authSuccess) {
	        return "authentication failed";
	    }
	    
	    List<Integer> issueIds = getIssueIds();
	    System.out.println(issueIds);
	    
        for (int id : issueIds) {
        	Map<Integer, Map<String, Double>> issueOptions = getIssueOptions(id);

        	Map<String, Double> nationsCurrentStats = getNationsCurrentStats();
        	int bestOption = chooseBestOption(nationsCurrentStats, nationsTargetStats, issueOptions);
        	System.out.println("Current stats: " 
        			+ nationsCurrentStats 
        			+ "Issue options: " 
        			+ issueOptions 
        			+ "Best option: " 
        			+ bestOption 
        			+ "\n");
        	
        	boolean executionSucceeded = executeOption(id, bestOption);
        	
        	while (!executionSucceeded) {
        		issueOptions.remove(bestOption);
        		
        		if (!issueOptions.isEmpty()) {
        			bestOption = chooseBestOption(nationsCurrentStats, nationsTargetStats, issueOptions);
        			executionSucceeded = executeOption(id, bestOption);
        		} else {
        			return "failure to execute";
        		}
        	}
        }
        
        System.out.printf("%n%n Instance Request Count: %d %n%n", requestCounter);
        
	    return "success";
	}
	
	/**
	 * Returns true if POST succeeds && response.body() doesn't include any failure message
	 * 
	 * @return authSuccess
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private Boolean authenticateNation() throws IOException, InterruptedException {
		String url = String.format("https://www.nationstates.net/cgi-bin/api.cgi?nation=%s&q=issues", nationName);
		String credential = "X-Password";
		
		HttpResponse<String> response = getRequest(url, credential);
		Boolean authSuccess;
		
	    if (response.body().contains("Unknown nation") || response.body().contains("Authentication Failed")) {
	    	authSuccess = false;
	    } else {
	    	authSuccess = true;
	    	nationPin = response.headers().firstValue("X-Pin").orElse("");
	    	System.out.printf("Authentication successful. PIN: %s%n", nationPin);
	    }
		
	    return authSuccess;
	}
	
	/**
	 * Gets a list of all issue ids using the ns api.
	 * 
	 * post: issueIds can be empty if no issues are found
	 * post: (issueIds.size() <= 5)
	 * 
	 * @return issueIds
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private List<Integer> getIssueIds() throws IOException, InterruptedException {
	    String issuesUrl = String.format("https://www.nationstates.net/cgi-bin/api.cgi?nation=%s&q=issues", nationName);
	    HttpResponse<String> response = getRequest(issuesUrl, getCredentialType());
	    List<Integer> issueIds = new ArrayList<>();
	    
	    Pattern pattern = Pattern.compile("<ISSUE id=\"(\\d+?)\">");
	    Matcher matcher = pattern.matcher(response.body());
	    
	    while (matcher.find()) {
	    	issueIds.add(Integer.parseInt(matcher.group(1)));
	    }
	    
	    return issueIds;
	}
	
	/**
	 * Gets effects of each option for an issue.
	 * 
	 * pre: issueId > 0
	 * 
	 * @param issueId: the ID of the issue to analyze.
	 * @return Map containing option numbers and their effects: {option={category=effect}}
	 * @throws IOException if there's an error fetching or parsing the page
	 */
	 private Map<Integer, Map<String, Double>> getIssueOptions(int issueId) throws IOException {
	    Map<Integer, Map<String, Double>> issueOptions = new HashMap<>();
	    String url = String.format("http://www.mwq.dds.nl/ns/results/%d.html", issueId);    
	    Document doc = Jsoup.connect(url).get();
	    
	    // get all rows except the header
	    Elements rows = doc.select("tr:gt(0)");
	    
	    for (Element row : rows) {
	        Elements columns = row.select("td");
	        if (columns.isEmpty()) continue;
	        
	        // get option number from the first column & convert to 0-based index (ns api takes option-1).
	        int optionNumber = Integer.parseInt(String.valueOf(columns.get(0).text().charAt(0))) - 1;
	        Map<String, Double> optionEffects = new HashMap<>();
	        
	        // second column holds option effects
            Elements divs = columns.get(1).select("div");
            for (Element div : divs) {
                String text = div.text();
                
                // parses category and mean score
                try {
                	Pattern pattern = Pattern.compile("^[+-]?\\d*(\\.\\d+)?\\s+to\\s[+-]?\\d*(\\.\\d+)?\\s+([\\w\\s]+)\\s+\\(mean\\s([+-]?\\d+(\\.\\d+))\\)$");
                	Matcher matcher = pattern.matcher(text);
                	
                	if (matcher.find()) {
	                    String category = matcher.group(3).trim();;    // "Civil Rights" or "Political Freedom"
	                    Double meanEffect = Double.parseDouble(matcher.group(4));
	
	                    switch (category) {
	                        case "Civil Rights":
	                        case "Economic Freedom":
	                        case "Political Freedom":
	                            optionEffects.put(category, meanEffect);
	                            break;
	                    }
                	}

                } catch (Exception e) {
                    // skips bad entries
                    continue;
                }
	        }
	        
	        optionEffects.putIfAbsent("Civil Rights", 0.0);
	        optionEffects.putIfAbsent("Economic Freedom", 0.0);
	        optionEffects.putIfAbsent("Political Freedom", 0.0);
	        
	        issueOptions.put(optionNumber, optionEffects);
	    }
	    
	    return issueOptions;
	}
	

	/**
	 * Retrieves current nation scores; civil rights, political freedom, and economic freedom.
	 * 
	 * post: a non null or empty map {category=score} representing the nations current scores.
	 * 
	 * @return Map containing the current statistics, or null if no match is found
	 * @throws IOException if there's an error with the HTTP request
	 * @throws InterruptedException if the HTTP request is interrupted
	 */
	public Map<String, Double> getNationsCurrentStats() throws IOException, InterruptedException {
        Map<String, Double> nationsCurrentStats = new HashMap<>();
        
	    String freedomScoresUrl = String.format("https://www.nationstates.net/cgi-bin/api.cgi?nation=%s&q=freedomscores&v=12", nationName);
	    String economicFreedomScoreUrl = String.format("https://www.nationstates.net/cgi-bin/api.cgi?nation=%s&q=census;scale=48&v=12", nationName);
	    
	    HttpResponse<String> freedomScoresResponse = getRequest(freedomScoresUrl, getCredentialType());
	    HttpResponse<String> economicFreedomScore = getRequest(economicFreedomScoreUrl, getCredentialType());
	    
	    Pattern freedomScoresPattern = Pattern.compile("<CIVILRIGHTS>(.*?)</CIVILRIGHTS>\\s*<ECONOMY>(.*?)</ECONOMY>\\s*<POLITICALFREEDOM>(.*?)</POLITICALFREEDOM>");
	    Pattern economicFreedomPattern = Pattern.compile("<SCALE id=\"48\">\\s*<SCORE>(.*?)</SCORE>");
	    
	    Matcher freedomScores = freedomScoresPattern.matcher(freedomScoresResponse.body());
	    Matcher economicFreedoms = economicFreedomPattern.matcher(economicFreedomScore.body());
	    
	    if (freedomScores.find() && economicFreedoms.find()) {
	        double civilRights = Double.parseDouble(freedomScores.group(1));
	        double politicalFreedom = Double.parseDouble(freedomScores.group(3));
	        double economicFreedom = Double.parseDouble(economicFreedoms.group(1));
	        
	        nationsCurrentStats.put("Civil Rights", civilRights);
	        nationsCurrentStats.put("Economic Freedom", economicFreedom);
	        nationsCurrentStats.put("Political Freedom", politicalFreedom);
	    }
	        
	    return nationsCurrentStats;
	}
		
	/**
	 * Chooses the option that gets closest to target stats using distance formula.
	 * Eg: sqrt((X1-Y1)^2 + (Xn-Yn)^2);
	 * 
	 * pre: !(currentStats.equals(null)) && is well formed {category=score}
	 * pre: !(targetStats.equals(null)) && is well formed {category=score}
	 * pre: !(options.equals(null)) && sub map is not null. {option={category=score}}
	 * 
	 * post: Returns the option that brings the user closest to their goal scores.
	 * 
	 * @param currentStats The current statistics of the nation
	 * @param targetStats The target statistics to aim for
	 * @param options Available options and their effects
	 * @return The option number that minimizes distance to target stats
	 * @throws IllegalArgumentException if input maps are null or empty
	 */
	public int chooseBestOption(Map<String, Double> currentStats, Map<String, Double> targetStats, Map<Integer, Map<String, Double>> options) {
	    if (currentStats == null || targetStats == null || options == null ||
	        currentStats.isEmpty() || targetStats.isEmpty() || options.isEmpty()) {
	        throw new IllegalArgumentException("Input maps cannot be null or empty");
	    }
	    
	    double bestDistance = Double.POSITIVE_INFINITY;
	    int bestOption = 0;
	    
	    for (Map.Entry<Integer, Map<String, Double>> entry : options.entrySet()) {
	        int optionNum = entry.getKey();
	        Map<String, Double> effects = entry.getValue();
	        double distance = 0.0;
	        
	        for (String category : currentStats.keySet()) {
	            double currentValue = currentStats.get(category);
	            
	            double effectValue = effects.get(category);
	            double currentValueWithEffect = currentValue + effectValue;
	            double targetValue = targetStats.get(category);
	            
	            distance += Math.pow(currentValueWithEffect - targetValue, 2);
	        }
	        
//	        distance = Math.sqrt(distance);
	        
	        if (distance < bestDistance) {
	            bestDistance = distance;
	            bestOption = optionNum;
	        }
	    }
	    
	    return bestOption;
	}

	/**
	 * Sends a post request with parameters to solve an issue.
	 * Returns boolean dependent on successful option execution.
	 * 
	 * pre: ((issueId >= 0) && (option >= 0))
	 * post: successfulExecution == true if valid choice executed
	 * 
	 * @param issueId the ID of the issue
	 * @param option the option number to submit
	 * @return true if the issue was successfully completed, false if failed
	 * @throws IOException if there's an error with the HTTP request
	 * @throws InterruptedException if the HTTP request is interrupted
	 */
	private boolean executeOption(int issueId, int option) throws IOException, InterruptedException {
	    String url = "https://www.nationstates.net/cgi-bin/api.cgi";
	    String parameters = String.format("nation=%s&c=issue&issue=%d&option=%d", nationName, issueId, option);
	    BodyPublisher payload = HttpRequest.BodyPublishers.ofString(parameters);
	  
	    HttpResponse<String> response = postRequest(url, getCredentialType(), payload);
	    System.out.println("Execute option response: " + response.body());
	    
	    boolean successfulExecution = !response.body().contains("Invalid choice");
	    System.out.println("Option " + option + " for issue " + issueId + 
	        (successfulExecution ? " executed successfully" : " was invalid"));
	        
	    return successfulExecution;
	}
	
	/**
	 * Returns a response from the request receiver at a url using auth.
	 * 
	 * pre: (!url.equals(null) && (!url.isBlank()) && (must be a valid url)
	 * pre: (!credential.equals(null) && !credential.isBlank()) && (must be either "X-Password || X-Pin")
	 * 
	 * post: increments requestCounter
	 * post: HttpResponse response (can be null)
	 * 
	 * @param url
	 * @param credential
	 * @return response
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private HttpResponse<String> getRequest(String url, String credential) throws IOException, InterruptedException {
		HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		
		String headerKey = (credential == "X-Pin") ? "X-Pin" : "X-Password";
		String headerValue = (headerKey == "X-Pin") ? nationPin : nationPassword;
		
		HttpRequest request = HttpRequest.newBuilder()
		        .uri(URI.create(url))
		        .header(headerKey, headerValue)
		        .header("User-Agent", String.format("%s | Contact: %s", APP_NAME, appContact))
		        .GET()
		        .build();
		
		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
		
		requestCounter++;
		return response;
	}
	
	/**
	 * Takes a payload and delivers it to a url address via POST request using auth headers.
	 * Returns the reciever's response
	 * 
	 * pre: (!url.equals(null) && (!url.isBlank()) && (must be a valid url)
	 * pre: (!credential.equals(null) && !credential.isBlank()) && (must be either "X-Password || X-Pin")
	 * pre: (!payload.equals(null)) && payload is well formed
	 * 
	 * post: increments requestCounter
	 * post: HttpResponse response (can be null)
	 * 
	 * @param url
	 * @param credential
	 * @param payload
	 * @return response
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private HttpResponse<String> postRequest(String url, String credential, BodyPublisher payload) throws IOException, InterruptedException {
		HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		
		String headerKey = (credential == "X-Pin") ? "X-Pin" : "X-Password";
		String headerValue = (headerKey == "X-Pin") ? nationPin : nationPassword;
		
		HttpRequest request = HttpRequest.newBuilder()
		        .uri(URI.create(url))
		        .header(headerKey, headerValue)
		        .header("User-Agent", String.format("%s | Contact: %s", APP_NAME, appContact))
		        .POST(payload)
		        .build();
		
		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
		
		requestCounter++;
		return response;
	}
	
	/**
	 * Returns X-Pin once it's been set and is not empty, otherwise returns X-Password
	 * 
	 * post: credentialType == "X-Password" || Password == "X-Pin"
	 * 
	 * @return credentialType
	 */
	private String getCredentialType() {
		String credentialType = (nationPin.isEmpty()) ? "X-Password" : "X-Pin";
		
		return credentialType;
	}
}
