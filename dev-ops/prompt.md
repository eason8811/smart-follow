
- smart-follow-api           → 接口层：只声明对外服务的接口方法签名与DTO（不放具体实现/控制器）
- smart-follow-trigger       → 触发层：实现 smart-follow-api 的接口（如 REST 控制器等），编排调用 domain
- smart-follow-domain        → 领域层：Ixxx接口、service实现、model(VO/Entity/Aggregate)、adapter/repository/port/event 接口
- smart-follow-infrastructure → 基础设施层：实现 domain 的 adapter/repository/port/event，DAO/PO、外部网关等
- smart-follow-app           → 启动层：唯一 Spring Boot 入口（Application、配置、拦截器等）
- smart-follow-types         → 公共类型：异常、枚举、工具等

