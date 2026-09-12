// Node 20+. Uses a closed workload with keep-alive connections; setup is not timed.
// Purchase mode registers distinct local test users; use only an isolated dataset.
import http from 'node:http';
import https from 'node:https';
import os from 'node:os';
import {performance} from 'node:perf_hooks';
import {mkdirSync, writeFileSync} from 'node:fs';

const args = Object.fromEntries(process.argv.slice(2).map(arg => {
    const index = arg.indexOf('=');
    if (index < 1) throw new Error('Use --name=value arguments');
    return [arg.slice(0, index).replace(/^--/, ''), arg.slice(index + 1)];
}));
const mode = args.mode || 'stock';
const base = new URL(args.base || 'http://localhost:8080');
const item = args.item;
const count = Number(args.requests || 10000);
const concurrency = Number(args.concurrency || 32);
if (!['stock', 'purchase'].includes(mode) || !item || !Number.isSafeInteger(count)
        || count < 1 || !Number.isSafeInteger(concurrency) || concurrency < 1) {
    throw new Error('Required: --item=ID [--mode=stock|purchase --requests=N --concurrency=N]');
}
const transport = base.protocol === 'https:' ? https : http;
const agent = new transport.Agent({keepAlive: true, maxSockets: concurrency});
const setupAgent = new transport.Agent({keepAlive: true, maxSockets: 8});

function request(path, options = {}, session = {}, activeAgent = agent) {
    return new Promise(resolve => {
        const start = performance.now();
        const body = options.body || '';
        const req = transport.request(new URL(path, base), {
            method: options.method || 'GET', agent: activeAgent,
            headers: {...options.headers, ...(session.cookie ? {Cookie: session.cookie} : {}),
                ...(body ? {'Content-Length': Buffer.byteLength(body)} : {})}
        }, res => {
            if (res.headers['set-cookie']) session.cookie = res.headers['set-cookie'][0].split(';')[0];
            let text = '';
            res.on('data', chunk => { text += chunk; });
            res.on('end', () => resolve({status: res.statusCode, ms: performance.now() - start, body: text}));
            res.on('error', error => resolve({status: 0, ms: performance.now() - start, body: error.code}));
        });
        req.setTimeout(15000, () => req.destroy(new Error('request timeout')));
        req.on('error', error => resolve({status: 0, ms: performance.now() - start, body: error.code}));
        req.end(body);
    });
}

async function run(total, workers, action) {
    let index = 0;
    const results = new Array(total);
    await Promise.all(Array.from({length: Math.min(total, workers)}, async () => {
        while (index < total) {
            const current = index++;
            results[current] = await action(current);
        }
    }));
    return results;
}

const initial = await request('/api/inventory/stock/' + encodeURIComponent(item));
if (initial.status !== 200) throw new Error('Stock endpoint must return 200 before measuring');
const initialStock = JSON.parse(initial.body);
const sessions = [];
if (mode === 'purchase') {
    const runId = Date.now().toString(36);
    await run(count, 8, async i => {
        const session = {};
        let token = JSON.parse((await request('/api/auth/csrf', {}, session, setupAgent)).body);
        const username = 'bench-' + runId + '-' + i;
        const password = 'local-benchmark-' + runId;
        const registered = await request('/api/auth/register', {
            method: 'POST', headers: {'Content-Type': 'application/json', [token.headerName]: token.token},
            body: JSON.stringify({username, password, email: username + '@example.com', fullName: 'Benchmark User'})
        }, session, setupAgent);
        if (registered.status !== 201) throw new Error('Registration failed: ' + registered.status);
        const login = await request('/perform_login', {
            method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({username, password, [token.parameterName]: token.token}).toString()
        }, session, setupAgent);
        if (login.status !== 302) throw new Error('Login failed: ' + login.status);
        const me = await request('/api/auth/me', {}, session, setupAgent);
        if (JSON.parse(me.body).authenticated !== 'true') throw new Error('Login did not establish a session');
        token = JSON.parse((await request('/api/auth/csrf', {}, session, setupAgent)).body);
        sessions[i] = {session, token};
    });
} else {
    await run(500, concurrency, () => request('/api/inventory/stock/' + encodeURIComponent(item)));
}
const start = performance.now();
const results = await run(count, concurrency, i => mode === 'stock'
    ? request('/api/inventory/stock/' + encodeURIComponent(item))
    : request('/api/orders?itemId=' + encodeURIComponent(item), {
        method: 'POST', headers: {[sessions[i].token.headerName]: sessions[i].token.token}
    }, sessions[i].session));
const durationMs = performance.now() - start;
const stockAfter = await request('/api/inventory/stock/' + encodeURIComponent(item));
const latencies = results.map(r => r.ms).sort((a, b) => a - b);
const percentile = p => latencies[Math.max(0, Math.ceil(p * count) - 1)];
const statusCounts = {};
for (const result of results) statusCounts[result.status] = (statusCounts[result.status] || 0) + 1;
const accepted = results.filter(r => r.status === 202);
const distinctOrders = new Set(accepted.map(r => JSON.parse(r.body).eventId)).size;
const expectedStatuses = mode === 'stock' ? [200] : [202, 409];
const report = {
    command: 'node ' + process.argv.slice(1).join(' '),
    timestamp: new Date().toISOString(), mode, item, requests: count, concurrency,
    runtime: process.version, os: {type: os.type(), release: os.release(), arch: os.arch(),
        cpus: os.cpus().length, model: os.cpus()[0]?.model, totalMemory: os.totalmem()},
    durationMs, throughput: count / (durationMs / 1000),
    latencyMs: {p50: percentile(.5), p95: percentile(.95), p99: percentile(.99), max: latencies.at(-1)},
    statusCounts, non2xxRate: results.filter(r => r.status < 200 || r.status >= 300).length / count,
    unexpectedErrorRate: results.filter(r => !expectedStatuses.includes(r.status)).length / count,
    initialStock, finalStock: stockAfter.status === 200 ? JSON.parse(stockAfter.body) : null,
    accepted: accepted.length, distinctOrders,
    oversells: mode === 'purchase' ? Math.max(0, distinctOrders - initialStock) : null,
    scope: 'Single host, closed workload; purchase setup excluded. Accepted orders are not payments. No production guarantee.'
};
mkdirSync('benchmarks/results', {recursive: true});
const output = 'benchmarks/results/' + mode + '-' + Date.now() + '.json';
writeFileSync(output, JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify(report, null, 2));
console.log('Saved ' + output);
agent.destroy();
setupAgent.destroy();
