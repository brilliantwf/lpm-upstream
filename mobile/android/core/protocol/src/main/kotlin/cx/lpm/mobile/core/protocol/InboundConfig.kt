package cx.lpm.mobile.core.protocol

import cx.lpm.mobile.core.model.ServiceInfo
import cx.lpm.mobile.core.model.bool
import cx.lpm.mobile.core.model.int
import cx.lpm.mobile.core.model.list
import cx.lpm.mobile.core.model.obj
import cx.lpm.mobile.core.model.string

internal val configParsers = parsers {
    put("readConfig") { o ->
        Inbound.ReadConfig(
            project = o.str("project"),
            layer = o.str("layer"),
            content = if (o.ok) o.str("content") else "",
            available = if (o.ok) o.bool("available") ?: true else false,
            error = o.failure("Couldn't read the config.")
        )
    }
    put("saveConfig") { o ->
        val project = o.str("project")
        Inbound.SaveConfig(
            project = project,
            layer = o.str("layer"),
            name = o.string("name") ?: project,
            error = o.failure("Couldn't save the config.")
        )
    }
    put("serviceBody") { o ->
        Inbound.ServiceBody(
            project = o.str("project"),
            key = o.str("key"),
            body = if (o.ok) o.obj("body") else null,
            source = o.str("source"),
            error = o.failure("Couldn't read the service.")
        )
    }
    put("actionBody") { o ->
        Inbound.ActionBody(
            project = o.str("project"),
            key = o.str("key"),
            body = if (o.ok) o.obj("body") else null,
            section = o.string("section") ?: "actions",
            source = o.str("source"),
            error = o.failure("Couldn't read the action.")
        )
    }
    put("saveService") { o ->
        Inbound.SaveService(o.str("project"), o.str("key"), o.failure("Couldn't save the service."))
    }
    put("deleteService") { o ->
        Inbound.DeleteService(
            o.str("project"),
            o.str("key"),
            o.failure("Couldn't delete the service.")
        )
    }
    put("saveProfile") { o ->
        Inbound.SaveProfile(
            o.str("project"),
            o.str("name"),
            o.failure("Couldn't save the profile.")
        )
    }
    put("deleteProfile") { o ->
        Inbound.DeleteProfile(
            o.str("project"),
            o.str("name"),
            o.failure("Couldn't delete the profile.")
        )
    }
    put("saveAction") { o ->
        Inbound.SaveAction(o.str("project"), o.str("key"), o.failure("Couldn't save the action."))
    }
    put("deleteAction") { o ->
        Inbound.DeleteAction(
            o.str("project"),
            o.str("key"),
            o.failure("Couldn't delete the action.")
        )
    }
    put("services") { o ->
        Inbound.Services(
            project = o.str("project"),
            running = o.bool("running") ?: false,
            services = if (o.ok) o.list("services", ServiceInfo::parse) else emptyList(),
            error = o.failure("Couldn't read services.")
        )
    }
    put("serviceLogs") { o ->
        Inbound.ServiceLogs(
            project = o.str("project"),
            paneIndex = o.int("paneIndex") ?: 0,
            text = if (o.ok) o.str("text") else null,
            error = o.failure("Couldn't read the logs.")
        )
    }
}
