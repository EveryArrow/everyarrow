/*
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Business Source License 1.1.
 * Use is subject to the LICENSE file, including
 * production, SaaS, and AI restrictions.
 */

package com.everyarrow.eventengine.core.services.monitoring;

public enum MonitoringServiceCallType {
    add_messages_to_job,
    add_messages_to_task,
    add_statistics_to_job,
    add_statistics_to_task,
    create_group,
    create_job,
    create_task,
    delete_group,
    delete_job,
    delete_task,
    end_group,
    end_job,
    end_task,
    get_all_instrument_unit_application_names,
    get_all_instrument_unit_system_names,
    get_environments,
    get_fastest_jobs,
    get_instrument_executions,
    get_instrument_executions_by_parent_id,
    get_instrument_executions_by_unit_parent_id,
    get_instrument_unit_executions,
    get_instrument_unit_last_executions_by_name,
    get_group,
    get_group_by_name,
    get_groups,
    get_job,
    get_job_by_name,
    get_job_executions_average_duration_grouped_by_date,
    get_job_executions_count,
    get_job_executions_count_grouped_by_date,
    get_jobs,
    get_jobs_metrics,
    get_jobs_success_rate,
    get_jobs_with_most_errors,
    get_least_executed_jobs,
    get_latest_job_executions,
    get_most_executed_jobs,
    get_slowest_jobs,
    get_slowest_jobs_by_average_duration,
    get_task,
    get_task_by_name,
    get_tasks,
    start_group,
    start_job,
    start_task,
    update_group,
    update_job,
    update_task,
}
