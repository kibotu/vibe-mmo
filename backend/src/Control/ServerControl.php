<?php declare(strict_types=1);

namespace Mmo\Control;

use Mmo\Config\Config;

final class ServerControl
{
    public function __construct(private readonly Config $config)
    {
        $driver = $this->driver();
        if (!in_array($driver, ['none', 'supervisorctl', 'systemctl'], true)) {
            throw new \InvalidArgumentException('Unsupported process control driver.');
        }
    }

    public function readOnly(): bool
    {
        if (!$this->config->bool('control.enabled', true) || !function_exists('proc_open')) {
            return true;
        }
        $driver = $this->driver();
        if ($driver === 'none') {
            return true;
        }
        if (!is_executable($this->binary())) {
            return true;
        }
        if ($driver === 'supervisorctl') {
            $config = $this->config->get('control.supervisor_config');
            $program = $this->config->get('control.supervisor_program');
            if (!is_string($config)
                || $config === ''
                || !is_file($config)
                || !is_readable($config)
                || !is_string($program)
                || preg_match('/^[A-Za-z0-9_.:-]{1,128}$/D', $program) !== 1
            ) {
                return true;
            }
        }
        if ($driver === 'systemctl') {
            $service = $this->config->get('control.service');
            if (!is_string($service) || preg_match('/^[A-Za-z0-9_.@-]{1,128}$/D', $service) !== 1) {
                return true;
            }
        }

        return false;
    }

    public function execute(string $action): ControlResult
    {
        if (!in_array($action, ['start', 'stop', 'restart'], true)) {
            return new ControlResult(false, 'Unsupported process action.');
        }
        if ($this->readOnly()) {
            return new ControlResult(false, 'Process control is unavailable; the dashboard is read-only.');
        }
        $command = $this->commandFor($action);
        if ($command === null) {
            return new ControlResult(false, 'Process control is not configured.');
        }

        $descriptors = [
            0 => ['pipe', 'r'],
            1 => ['pipe', 'w'],
            2 => ['pipe', 'w'],
        ];
        $process = @proc_open($command, $descriptors, $pipes, null, null, ['bypass_shell' => true]);
        if (!is_resource($process)) {
            return new ControlResult(false, 'The process control command could not be started.');
        }
        fclose($pipes[0]);
        stream_set_blocking($pipes[1], false);
        stream_set_blocking($pipes[2], false);
        $output = '';
        $deadline = microtime(true) + 5.0;
        do {
            $output .= stream_get_contents($pipes[1]);
            $output .= stream_get_contents($pipes[2]);
            $status = proc_get_status($process);
            if (!$status['running']) {
                break;
            }
            usleep(50_000);
        } while (microtime(true) < $deadline);
        if (($status['running'] ?? false) === true) {
            proc_terminate($process);
        }
        fclose($pipes[1]);
        fclose($pipes[2]);
        $exitCode = proc_close($process);
        $output = trim(substr($output, -1024));
        $ok = $exitCode === 0;
        $message = $ok ? 'Process command completed.' : 'Process command failed.';
        if (!$ok && $output !== '') {
            $message .= ' ' . $output;
        }

        return new ControlResult($ok, $message, implode(' ', array_map('escapeshellarg', $command)));
    }

    /** @return list<string>|null */
    public function commandFor(string $action): ?array
    {
        $driver = $this->driver();
        if ($driver === 'supervisorctl') {
            $config = $this->config->string('control.supervisor_config');
            $program = $this->config->string('control.supervisor_program');

            return [$this->binary(), '-c', $config, $action, $program];
        }
        if ($driver === 'systemctl') {
            return [$this->binary(), $action, $this->config->string('control.service')];
        }

        return null;
    }

    private function driver(): string
    {
        $driver = strtolower(trim($this->config->string('control.driver', 'none')));

        return $driver === 'supervisor' ? 'supervisorctl' : $driver;
    }

    private function binary(): string
    {
        $default = $this->driver() === 'supervisorctl' ? '/usr/bin/supervisorctl' : '/usr/bin/systemctl';

        return $this->config->string('control.binary', $default);
    }
}
