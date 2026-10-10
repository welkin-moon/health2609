import Foundation

// MARK: - HealthApi Errors

public enum HealthApiError: LocalizedError, Sendable {
    case invalidURL
    case httpError(statusCode: Int, message: String)
    case decodingError(String)
    case networkError(String)
    case fileTooLarge

    public var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "无效的接口请求地址"
        case .httpError(let code, let msg):
            return "服务请求失败 (\(code)): \(msg)"
        case .decodingError(let msg):
            return "数据解析失败: \(msg)"
        case .networkError(let msg):
            return "网络连接错误: \(msg)"
        case .fileTooLarge:
            return "上传图片过大，不能超过 8MB"
        }
    }
}

// MARK: - HealthApi Client Protocol & Implementation

public protocol HealthApiClient: Sendable {
    func todayMenu(date: String, mealSlot: String) async throws -> TodayMenuDto
    func todaySummary(date: String) async throws -> DailySummaryDto
    func schoolDayWindows(date: String) async throws -> SchoolDayWindowsDto
    func saveMeal(request: MealConsumptionRequest) async throws -> ApiWriteResult
    func saveOutsideSchoolActivity(request: OutsideSchoolActivityRequest) async throws -> ApiWriteResult
    func saveManualActivity(request: ManualActivityRequest) async throws -> ApiWriteResult
    func saveEnergyReference(request: EnergyReferenceRequest) async throws -> ApiWriteResult
    func analyzeHomeMeal(imageData: Data, mimeType: String, fileName: String) async throws -> HomeMealAnalysisResultDto
    func saveHomeMeal(request: ConfirmedHomeMealRequest) async throws -> ApiWriteResult
}

public actor HealthApi: HealthApiClient {
    public static let shared = HealthApi()

    public let baseURL: URL
    private let session: URLSession

    private let defaultHeaders: [String: String] = [
        "x-demo-school": "demo-school",
        "x-demo-participant": "demo-student",
        "x-demo-role": "student",
        "Accept": "application/json"
    ]

    public init(baseURL: URL = URL(string: "https://h2609.lunarlab.uk")!) {
        self.baseURL = baseURL
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30.0
        config.timeoutIntervalForResource = 120.0
        self.session = URLSession(configuration: config)
    }

    // MARK: - API Methods

    public func todayMenu(date: String, mealSlot: String = "lunch") async throws -> TodayMenuDto {
        var components = URLComponents(url: baseURL.appendingPathComponent("v1/today/menu"), resolvingAgainstBaseURL: true)
        components?.queryItems = [
            URLQueryItem(name: "date", value: date),
            URLQueryItem(name: "mealSlot", value: mealSlot)
        ]
        guard let url = components?.url else { throw HealthApiError.invalidURL }
        return try await perform(request: makeRequest(url: url, method: "GET"))
    }

    public func todaySummary(date: String) async throws -> DailySummaryDto {
        var components = URLComponents(url: baseURL.appendingPathComponent("v1/today/summary"), resolvingAgainstBaseURL: true)
        components?.queryItems = [
            URLQueryItem(name: "date", value: date)
        ]
        guard let url = components?.url else { throw HealthApiError.invalidURL }
        return try await perform(request: makeRequest(url: url, method: "GET"))
    }

    public func schoolDayWindows(date: String) async throws -> SchoolDayWindowsDto {
        var components = URLComponents(url: baseURL.appendingPathComponent("v1/school/day-windows"), resolvingAgainstBaseURL: true)
        components?.queryItems = [
            URLQueryItem(name: "date", value: date)
        ]
        guard let url = components?.url else { throw HealthApiError.invalidURL }
        return try await perform(request: makeRequest(url: url, method: "GET"))
    }

    public func saveMeal(request: MealConsumptionRequest) async throws -> ApiWriteResult {
        let url = baseURL.appendingPathComponent("v1/meals/consumption")
        var req = makeRequest(url: url, method: "POST")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONEncoder().encode(request)
        return try await perform(request: req)
    }

    public func saveOutsideSchoolActivity(request: OutsideSchoolActivityRequest) async throws -> ApiWriteResult {
        let url = baseURL.appendingPathComponent("v1/activity/outside-school")
        var req = makeRequest(url: url, method: "POST")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONEncoder().encode(request)
        return try await perform(request: req)
    }

    public func saveManualActivity(request: ManualActivityRequest) async throws -> ApiWriteResult {
        let url = baseURL.appendingPathComponent("v1/activity/manual")
        var req = makeRequest(url: url, method: "POST")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONEncoder().encode(request)
        return try await perform(request: req)
    }

    public func saveEnergyReference(request: EnergyReferenceRequest) async throws -> ApiWriteResult {
        let url = baseURL.appendingPathComponent("v1/preferences/energy-reference")
        var req = makeRequest(url: url, method: "PUT")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONEncoder().encode(request)
        return try await perform(request: req)
    }

    public func analyzeHomeMeal(
        imageData: Data,
        mimeType: String = "image/jpeg",
        fileName: String = "meal.jpg"
    ) async throws -> HomeMealAnalysisResultDto {
        guard !imageData.isEmpty else {
            throw HealthApiError.networkError("图片数据为空")
        }
        guard imageData.count <= 8 * 1024 * 1024 else {
            throw HealthApiError.fileTooLarge
        }

        let url = baseURL.appendingPathComponent("v1/home-meals/analyze")
        let boundary = "Boundary-\(UUID().uuidString)"
        var req = makeRequest(url: url, method: "POST")
        req.timeoutInterval = 120
        req.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"image\"; filename=\"\(fileName)\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: \(mimeType)\r\n\r\n".data(using: .utf8)!)
        body.append(imageData)
        body.append("\r\n".data(using: .utf8)!)
        body.append("--\(boundary)--\r\n".data(using: .utf8)!)
        req.httpBody = body

        return try await perform(request: req)
    }

    public func saveHomeMeal(request: ConfirmedHomeMealRequest) async throws -> ApiWriteResult {
        let url = baseURL.appendingPathComponent("v1/home-meals")
        var req = makeRequest(url: url, method: "POST")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONEncoder().encode(request)
        return try await perform(request: req)
    }

    // MARK: - Private Helpers

    private func makeRequest(url: URL, method: String) -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = method
        for (key, val) in defaultHeaders {
            request.setValue(val, forHTTPHeaderField: key)
        }
        request.setValue(UserDefaults.standard.string(forKey: "schoolId") ?? "demo-school", forHTTPHeaderField: "x-demo-school")
        request.setValue(UserDefaults.standard.string(forKey: "participantId") ?? "demo-student", forHTTPHeaderField: "x-demo-participant")
        return request
    }

    private func perform<T: Decodable>(request: URLRequest) async throws -> T {
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw HealthApiError.networkError("无效的网络响应")
        }

        guard (200...299).contains(httpResponse.statusCode) else {
            let errorMsg = String(data: data, encoding: .utf8) ?? "未知错误"
            throw HealthApiError.httpError(statusCode: httpResponse.statusCode, message: errorMsg)
        }

        do {
            let decoder = JSONDecoder()
            return try decoder.decode(T.self, from: data)
        } catch {
            throw HealthApiError.decodingError(error.localizedDescription)
        }
    }
}
