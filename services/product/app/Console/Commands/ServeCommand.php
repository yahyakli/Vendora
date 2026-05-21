<?php

namespace App\Console\Commands;

use Illuminate\Foundation\Console\ServeCommand as BaseServeCommand;
use Symfony\Component\Process\Process;

class ServeCommand extends BaseServeCommand
{
    /**
     * The console command description.
     *
     * @var string
     */
    protected $description = 'Serve the application on the PHP development server with auto-Docker infrastructure';

    /**
     * The console command name.
     *
     * @var string
     */
    protected $name = 'serve';

    /**
     * Execute the console command.
     *
     * @return int
     *
     * @throws \Exception
     */
    public function handle()
    {
        if ($this->shouldStartDocker()) {
            $this->info('Starting infrastructure in Docker (compose.yaml)...');
            
            $process = new Process(['docker', 'compose', 'up', '-d']);
            $process->setWorkingDirectory(base_path());
            $process->setTimeout(null);
            $process->run();

            if (!$process->isSuccessful()) {
                $this->warn('Could not start Docker infrastructure. Ensure Docker is running.');
                $this->line($process->getErrorOutput());
            } else {
                $this->info('Infrastructure is ready.');
                
                // Register shutdown function to stop docker
                register_shutdown_function(function () {
                    $this->newLine();
                    $this->info('Shutting down Docker infrastructure...');
                    $stopProcess = new Process(['docker', 'compose', 'down']);
                    $stopProcess->setWorkingDirectory(base_path());
                    $stopProcess->run();
                    $this->info('Infrastructure stopped.');
                });
            }
        }

        return parent::handle();
    }

    /**
     * Determine if we should attempt to start Docker.
     *
     * @return bool
     */
    protected function shouldStartDocker()
    {
        return file_exists(base_path('compose.yaml')) || file_exists(base_path('docker-compose.yml'));
    }
}
