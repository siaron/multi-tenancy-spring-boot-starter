package cc.sofast.infrastructure.tenant.notify;

import cc.sofast.infrastructure.tenant.datasource.DataSourceProperty;
import cc.sofast.infrastructure.tenant.datasource.TenantDataSourceRegister;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author apple
 */
public class TenantEventProcess implements StreamListener<String, MapRecord<String, String, String>> {
    private static final Logger logger = LoggerFactory.getLogger(TenantEventProcess.class);
    private final ObjectMapper objectMapper;
    private final TenantDataSourceRegister tenantDataSourceRegister;
    private final TenantEventNotifyProperties tenantEventNotifyProperties;
    private final Map<TenantEventType, Consumer<TenantEvent>> processorConsumer;

    public TenantEventProcess(ObjectMapper objectMapper, TenantDataSourceRegister tenantDataSourceRegister, TenantEventNotifyProperties tenantEventNotifyProperties) {
        this.objectMapper = objectMapper;
        this.tenantDataSourceRegister = tenantDataSourceRegister;
        this.tenantEventNotifyProperties = tenantEventNotifyProperties;
        this.processorConsumer = new HashMap<>();
        initProcessorConsumer();
    }

    private void initProcessorConsumer() {
        this.processorConsumer.put(TenantEventType.CREATE, this::createTenant);
        this.processorConsumer.put(TenantEventType.DELETE, this::deleteTenant);
    }

    private void deleteTenant(TenantEvent tenantEvent) {
        if (!CollectionUtils.isEmpty(tenantEvent.getTenants())) {
            for (String tenant : tenantEvent.getTenants()) {
                tenantDataSourceRegister.remove(tenant);
            }
        }
    }

    private void createTenant(TenantEvent tenantEvent) {
        if (!CollectionUtils.isEmpty(tenantEvent.getTenants())) {
            for (String tenant : tenantEvent.getTenants()) {
                //TODO 1
                DataSourceProperty dataSourceProperty = new DataSourceProperty();
                tenantDataSourceRegister.register(tenant, dataSourceProperty);
            }
        }
    }

    /**
     * @param message never {@literal null}.
     */
    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        Map<String, String> messageValue = message.getValue();
        if (!CollectionUtils.isEmpty(messageValue)) {
            String val = messageValue.get(tenantEventNotifyProperties.getStream().getKey());
            try {
                TenantEvent tenantEvent = objectMapper.readValue(val, new TypeReference<>() {
                });
                Consumer<TenantEvent> tenantEventConsumer = processorConsumer.get(tenantEvent.getType());
                tenantEventConsumer.accept(tenantEvent);
                logger.info("process tenant event success id: [{}], event: [{}]", message.getId(), val);
            } catch (JsonProcessingException e) {
                logger.error("failed parser tenantEvent.", e);
            }
        }

    }
}
