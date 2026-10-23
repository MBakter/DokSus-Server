package hr.alu.okiru.doksusserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.web.config.EnableSpringDataWebSupport

/**
 * Entry point of the DokSus backend. Starting this class boots Spring, which scans the
 * `hr.alu.okiru.doksusserver` package and wires together every controller, service and repository.
 */
@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
class DokSusServerApplication

fun main(args: Array<String>) {
    runApplication<DokSusServerApplication>(*args)
}
