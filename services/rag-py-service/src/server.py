# gRPC Server - RAG 服务入口（集成 Nacos 服务注册）
import asyncio
import signal
import sys

import grpc
from concurrent import futures

from config.nacos_config import NACOS_CONFIG
from src.grpc_stubs import rag_service_pb2, rag_service_pb2_grpc
from src.nacos_registry import NacosRegistry
from src.pipeline import rag_pipeline


class RAGServiceServicer(rag_service_pb2_grpc.RAGServiceServicer):
    """RAG gRPC 服务实现"""

    def AskStream(self, request, context):
        question = request.question.strip()
        if not question:
            context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
            context.set_details("question 不能为空")
            return

        for chunk in rag_pipeline.ask_stream(question):
            yield rag_service_pb2.StreamChunk(chunk=chunk)


class RAGServer:
    def __init__(
        self,
        host: str = "0.0.0.0",
        port: int = 50051,
        nacos_enabled: bool = True,
    ):
        self.host = host
        self.port = port
        self.nacos_enabled = nacos_enabled
        self._grpc_server: grpc.Server | None = None
        self._registry: NacosRegistry | None = None

    async def start(self):
        """启动 gRPC Server 并注册到 Nacos"""
        # 1. 启动 gRPC Server
        self._grpc_server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
        rag_service_pb2_grpc.add_RAGServiceServicer_to_server(
            RAGServiceServicer(), self._grpc_server
        )
        self._grpc_server.add_insecure_port(f"{self.host}:{self.port}")
        self._grpc_server.start()
        print(f"gRPC RAG Server 已启动: {self.host}:{self.port}")

        # 2. 注册到 Nacos
        if self.nacos_enabled:
            try:
                self._registry = NacosRegistry(
                    server_address=NACOS_CONFIG["server_address"],
                    namespace_id=NACOS_CONFIG["namespace_id"],
                    group_name=NACOS_CONFIG["group_name"],
                    service_name=NACOS_CONFIG["service_name"],
                    service_ip=NACOS_CONFIG["service_ip"],
                    service_port=NACOS_CONFIG["service_port"],
                )
                await self._registry.register()
            except Exception as e:
                print(f"[Nacos] 注册失败: {e}")
                print(
                    "[Nacos] 提示: 如不需要 Nacos，可运行: "
                    "python -m src.server --no-nacos"
                )

    async def stop(self):
        """注销 Nacos 并停止 gRPC Server"""
        if self._registry:
            try:
                await self._registry.deregister()
                await self._registry.close()
            except Exception as e:
                print(f"[Nacos] 注销异常: {e}")

        if self._grpc_server:
            self._grpc_server.stop(grace=5)
            print("gRPC RAG Server 已停止")


async def main():
    # 解析命令行参数
    nacos_enabled = "--no-nacos" not in sys.argv
    # gRPC server 绑定 0.0.0.0 以接受来自任意网卡的连接
    # Nacos 注册使用的 IP 在 nacos_config.py 中单独配置
    bind_host = "0.0.0.0"
    port = NACOS_CONFIG["service_port"]

    server = RAGServer(
        host=bind_host,
        port=port,
        nacos_enabled=nacos_enabled,
    )

    await server.start()
    print("服务运行中，按 Ctrl+C 停止...")

    # 等待停止信号
    stop_event = asyncio.Event()

    def _signal_handler():
        stop_event.set()

    # Windows: 用 signal.signal + asyncio.Event 的简单方式
    signal.signal(signal.SIGINT, lambda s, f: _signal_handler())
    signal.signal(signal.SIGTERM, lambda s, f: _signal_handler())

    await stop_event.wait()
    print("\n正在关闭服务...")
    await server.stop()


if __name__ == "__main__":
    asyncio.run(main())
