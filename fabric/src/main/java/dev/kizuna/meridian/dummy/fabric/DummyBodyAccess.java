package dev.kizuna.meridian.dummy.fabric;

import dev.kizuna.meridian.dummy.server.DummyResetPlan;
import dev.kizuna.meridian.protocol.HealthHostProtobuf;
import dev.kizuna.meridian.protocol.MeridianProtocol;
import dev.kizuna.meridian.protocol.pb.BodyMessages.BodySnapshot;
import dev.kizuna.meridian.protocol.pb.HealthHostMessages.HealthHostRequest;
import dev.kizuna.meridian.protocol.pb.HealthHostMessages.HealthHostResponse;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.BodyCommand;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.RequestEnvelope;
import dev.kizuna.meridian.server.BodyHealthHost;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;
import java.util.UUID;

/** 通过 Framework 健康宿主协议读取与修改假人身体，不接触 Threatening 的内部状态。 */
public final class DummyBodyAccess {
    private static final String HEALTH_HOST_ENTRYPOINT = "meridian_health_host";
    private static final int HOST_PROTOCOL_VERSION = 1;
    private static List<BodyHealthHost> hosts = List.of();

    private DummyBodyAccess() {
        // 宿主列表在入口绑定一次，查询不缓存身体。
    }

    static void bindHosts() {
        hosts = List.copyOf(FabricLoader.getInstance().getEntrypoints(HEALTH_HOST_ENTRYPOINT, BodyHealthHost.class));
    }

    public static BodySnapshot inspect(LivingEntity entity) {
        HealthHostRequest.Builder request = HealthHostRequest.newBuilder();
        request.setProtocolVersion(HOST_PROTOCOL_VERSION);
        request.setRequestId(UUID.randomUUID().toString());
        request.setSubjectId(entity.getStringUUID());
        request.setInspect(true);
        return exchange(request.build());
    }

    public static void reset(LivingEntity entity) {
        // 计划按重置前的快照一次算出；每条命令提交前重新读取，绑定最新身体 ID 与版本。
        List<BodyCommand> plan = DummyResetPlan.commands(inspect(entity));
        for (BodyCommand command : plan) {
            BodySnapshot latest = inspect(entity);
            submit(entity, latest, command);
        }
    }

    private static void submit(LivingEntity entity, BodySnapshot body, BodyCommand command) {
        String requestId = UUID.randomUUID().toString();
        BodyCommand.Builder bound = command.toBuilder();
        bound.setBodyId(body.getId());
        bound.setExpectedRevision(body.getRevision());
        RequestEnvelope.Builder envelope = RequestEnvelope.newBuilder();
        envelope.setProtocolVersion(MeridianProtocol.VERSION);
        envelope.setRequestId(requestId);
        envelope.setBodyCommand(bound);
        HealthHostRequest.Builder request = HealthHostRequest.newBuilder();
        request.setProtocolVersion(HOST_PROTOCOL_VERSION);
        request.setRequestId(requestId);
        request.setSubjectId(entity.getStringUUID());
        request.setEffect(envelope);
        exchange(request.build());
    }

    private static BodySnapshot exchange(HealthHostRequest request) {
        // 恰好一个宿主认领；无人认领、重复认领或宿主拒绝都直接报错。
        BodySnapshot found = null;
        byte[] encoded = HealthHostProtobuf.encodeRequest(request);
        for (BodyHealthHost host : hosts) {
            HealthHostResponse response = HealthHostProtobuf.decodeResponse(host.exchange(encoded));
            if (!response.getRequestId().equals(request.getRequestId())) {
                throw new IllegalStateException("身体宿主响应身份不匹配");
            }
            if (response.hasFailure()) {
                throw new IllegalStateException("身体宿主拒绝请求：" + response.getFailure().getCode());
            }
            if (response.hasUnbound()) {
                continue;
            }
            if (!response.hasBody()) {
                throw new IllegalStateException("身体宿主响应缺少身体");
            }
            if (found != null) {
                throw new IllegalStateException("训练假人被多个身体宿主认领");
            }
            found = response.getBody();
        }
        if (found == null) {
            throw new IllegalStateException("训练假人没有身体");
        }
        return found;
    }
}
