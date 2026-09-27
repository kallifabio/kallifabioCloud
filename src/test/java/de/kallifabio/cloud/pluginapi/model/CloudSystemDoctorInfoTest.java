package de.kallifabio.cloud.pluginapi.model;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CloudSystemDoctorInfoTest {

    @Test
    void parsesSummaryAndFindings() {
        var json = JsonParser.parseString("""
                {
                  "timestamp": 1770000000000,
                  "summary": {
                    "state": "CRITICAL",
                    "critical": 1,
                    "warnings": 2,
                    "info": 3,
                    "findings": 6,
                    "groups": 4
                  },
                  "findings": [
                    {
                      "severity": "CRITICAL",
                      "id": "port:collision:25577",
                      "message": "Port collision",
                      "recommendation": "Use unique ports."
                    },
                    {
                      "severity": "WARNING",
                      "id": "network:backendBind",
                      "message": "Backend bind enforcement is disabled.",
                      "recommendation": "Enable backend bind enforcement."
                    }
                  ]
                }
                """).getAsJsonObject();

        CloudSystemDoctorInfo info = CloudSystemDoctorInfo.from(json);

        Assertions.assertEquals(1770000000000L, info.timestamp());
        Assertions.assertEquals("CRITICAL", info.state());
        Assertions.assertEquals(1, info.criticalCount());
        Assertions.assertEquals(2, info.warningCount());
        Assertions.assertEquals(3, info.infoCount());
        Assertions.assertEquals(6, info.findingCount());
        Assertions.assertEquals(4, info.groupCount());
        Assertions.assertTrue(info.hasCriticalFindings());
        Assertions.assertFalse(info.ok());
        Assertions.assertEquals(2, info.findings().size());
        Assertions.assertTrue(info.findings().get(0).critical());
        Assertions.assertTrue(info.findings().get(1).warning());
        Assertions.assertEquals("Use unique ports.", info.findings().get(0).recommendation());
    }

    @Test
    void handlesMissingFieldsDefensively() {
        CloudSystemDoctorInfo info = CloudSystemDoctorInfo.from(JsonParser.parseString("{}").getAsJsonObject());

        Assertions.assertEquals("UNKNOWN", info.state());
        Assertions.assertEquals(0, info.findingCount());
        Assertions.assertTrue(info.findings().isEmpty());
        Assertions.assertFalse(info.hasCriticalFindings());
    }
}
