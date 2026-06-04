package com.frauddetection.cluster_controller.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

// =============================================================================
// DockerOrchestratorServiceImpl — The Docker-Based Scaling Engine
//
// This class is the "Docker" strategy for orchestrating our containers.
// It uses Java's ProcessBuilder to execute Docker Compose CLI commands
// directly on the host machine.
//
// HOW DOES JAVA TALK TO DOCKER?
//   There are two main approaches:
//   1. The docker-java library (a Java API that speaks Docker's REST protocol).
//   2. The ProcessBuilder approach (Java runs `docker compose` as a shell command).
//
//   We chose ProcessBuilder because:
//     - It uses the EXACT same `docker compose` command you type in your terminal.
//     - It respects your docker-compose.yml file's networks, volumes, and configs.
//     - It's simpler to understand and debug.
//     - When we migrate to Kubernetes, this entire class gets swapped out anyway.
//
// WHEN DOES SPRING USE THIS CLASS?
//   Only when the active Spring Profile is "docker" (set in application.yml).
//   The @Profile("docker") annotation tells Spring: "Only create this bean
//   if the profile is 'docker'. If the profile is 'kubernetes', ignore me."
// =============================================================================
@Service
@Profile("docker")
public class DockerOrchestratorServiceImpl implements OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(DockerOrchestratorServiceImpl.class);

    // This value is injected from application.yml → cluster.compose-project-dir.
    // It tells us WHERE the docker-compose.yml file lives on disk, because
    // `docker compose` commands need to be run from that directory.
    @Value("${cluster.compose-project-dir}")
    private String composeProjectDir;

    /**
     * Scale a Docker Compose service to the desired number of replicas.
     *
     * Under the hood, this runs the equivalent of typing in your terminal:
     *   docker compose up -d --scale producer=5 --no-recreate
     *
     * The --no-recreate flag is important: it tells Docker "don't restart
     * containers that are already running, just add/remove as needed."
     * Without it, Docker would destroy ALL existing containers and create
     * fresh ones, causing unnecessary downtime.
     */
    @Override
    public String scaleService(String serviceName, int replicas) throws Exception {
        log.info("Scaling service '{}' to {} replicas via Docker Compose", serviceName, replicas);

        // Build the command as an array of strings.
        // ProcessBuilder takes each argument separately (not as one big string)
        // to avoid shell injection vulnerabilities.
        String[] command = {
            "docker", "compose",
            "up", "-d",
            "--scale", serviceName + "=" + replicas,
            "--no-recreate"
        };

        // Execute the command and capture the result.
        String output = executeCommand(command);
        log.info("Scale command completed. Output: {}", output);

        return "Scaled " + serviceName + " to " + replicas + " replicas";
    }

    /**
     * Count how many containers of a specific service are currently RUNNING.
     *
     * This runs:
     *   docker compose ps --status running --format "{{.Name}}" producer
     *
     * The output is one container name per line. We count the lines to get
     * the number of running containers. If there are 3 producers running,
     * the output would look like:
     *   fraud-detection-producer-1
     *   fraud-detection-producer-2
     *   fraud-detection-producer-3
     */
    @Override
    public int getRunningCount(String serviceName) throws Exception {
        String[] command = {
            "docker", "compose",
            "ps",
            "--status", "running",
            "--format", "{{.Name}}",
            serviceName
        };

        String output = executeCommand(command);

        // If the output is empty, no containers are running.
        if (output == null || output.isBlank()) {
            return 0;
        }

        // Count the number of non-empty lines in the output.
        // Each line = one running container.
        int count = (int) output.lines()
                .filter(line -> !line.isBlank())
                .count();

        log.debug("Service '{}' has {} running containers", serviceName, count);
        return count;
    }

    /**
     * Determine the health status of a service based on its running container count.
     *
     * The logic is simple:
     *   - 0 containers running → "STOPPED"
     *   - 1+ containers running → "HEALTHY"
     *
     * In a more advanced version, we could check CPU usage, memory, or
     * container health checks to return "DEGRADED" for containers that
     * are alive but struggling.
     */
    @Override
    public String getServiceStatus(String serviceName) throws Exception {
        int count = getRunningCount(serviceName);

        if (count == 0) {
            return "STOPPED";
        }
        return "HEALTHY";
    }

    // =========================================================================
    // Private Helper: Execute a Shell Command
    //
    // This is the core method that actually runs commands on the host machine.
    // It's a reusable utility — every public method above calls this.
    //
    // HOW ProcessBuilder WORKS:
    //   1. We create a ProcessBuilder with the command we want to run.
    //   2. We set the working directory to where docker-compose.yml lives.
    //   3. We start the process and read its output (stdout).
    //   4. We wait for it to finish (with a timeout so we don't hang forever).
    //   5. If the command fails (exit code != 0), we throw an exception.
    // =========================================================================
    private String executeCommand(String[] command) throws Exception {
        log.debug("Executing command: {}", String.join(" ", command));

        ProcessBuilder processBuilder = new ProcessBuilder(command);

        // Set the working directory to the docker-compose project root.
        // Docker Compose needs to be run from the directory containing
        // the docker-compose.yml file, otherwise it can't find the services.
        processBuilder.directory(new File(composeProjectDir));

        // Redirect stderr into stdout so we capture ALL output in one stream.
        // This is important because Docker sometimes sends progress info to stderr.
        processBuilder.redirectErrorStream(true);

        // Start the process (this is when the command actually begins executing).
        Process process = processBuilder.start();

        // Read all output from the process into a single String.
        // BufferedReader reads line-by-line which is efficient for text output.
        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }

        // Wait for the process to finish. If it takes more than 30 seconds,
        // something is seriously wrong and we should stop waiting.
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Command timed out after 30 seconds: "
                    + String.join(" ", command));
        }

        // Check the exit code. In Unix/Linux convention:
        //   exit code 0 = success
        //   exit code != 0 = something went wrong
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code " + exitCode
                    + ". Output: " + output);
        }

        return output;
    }
}
