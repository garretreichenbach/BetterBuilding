import requests

class LLMEntryPoint:

	def __init__(self):
		self.url = "http://192.168.1.197:8000/v1/chat/completions"
        self.headers = {"Content-Type": "application/json"}

    def prompt(self, prompt):
		data = {
			"model": "google/gemma-3-12b",
			"messages": [
				{"role": "system", "content": "You are a helpful assistant."},
				{"role": "user", "content": prompt}
			]
		}
		response = requests.post(self.url, headers=self.headers, json=data)
		if response.status_code == 200:
			return response.json()
		else:
			return {"error": f"Request failed with status code {response.status_code}"}

if __name__ == "__main__":
    entry_point = LLMEntryPoint()
    server = GatewayServer(entry_point=entry_point, port=25333)
    server.start()
    print("Gateway Server Started")