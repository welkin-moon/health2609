#!/usr/bin/env node
// This performs an SDK build. It never substitutes a source zip for a HAP.
import { cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';

const args = process.argv.slice(2);
function option(name, fallback) { const index = args.indexOf(name); return index < 0 ? fallback : args[index + 1]; }
const platform = option('--platform', 'harmony');
if (!['harmony', 'openharmony'].includes(platform)) throw new Error('--platform must be harmony or openharmony');
const signed = args.includes('--signed');
const repo = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const out = resolve(option('--out', join(repo, 'dist', platform)));
const tools = process.env.HARMONY_COMMANDLINE_TOOLS;
const suffix = process.platform === 'win32' ? '.bat' : '';
function tool(name, override, relative) {
  const binary = process.env[override] || (tools ? join(tools, relative, name + suffix) : name + suffix);
  if ((process.env[override] || tools) && !existsSync(binary)) throw new Error(`Missing ${name}: ${binary}`);
  return binary;
}
const hvigor = tool('hvigorw', 'HVIGOR_EXECUTABLE', 'hvigor/bin');
const ohpm = tool('ohpm', 'OHPM_EXECUTABLE', 'ohpm/bin');
// The plugin uses Hvigor's internal APIs, not merely its public major version.
// Match the engine shipped in the selected SDK instead of installing latest 5.x.
function packageInventory(root) {
  const packages = [];
  const pending = [root];
  while (pending.length > 0) {
    const dir = pending.pop();
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const path = join(dir, entry.name);
      // Symlinks can escape the SDK and create recursive walks; skip them.
      if (entry.isDirectory()) pending.push(path);
      else if (entry.isFile() && entry.name === 'package.json') {
        try {
          const metadata = JSON.parse(readFileSync(path, 'utf8'));
          if (['@ohos/hvigor', '@ohos/hvigor-ohos-plugin'].includes(metadata.name)) {
            packages.push({ path, ...metadata });
          }
        } catch { /* Non-package JSON is irrelevant to engine selection. */ }
      }
    }
  }
  return packages;
}
let hvigorVersion;
let pluginVersion = process.env.HVIGOR_PLUGIN_VERSION;
if (tools) {
  const hvigorDir = join(tools, 'hvigor');
  if (!existsSync(hvigorDir)) throw new Error('SDK Hvigor directory missing: ' + hvigorDir);
  const inventory = packageInventory(hvigorDir);
  const engines = inventory.filter(pkg => pkg.name === '@ohos/hvigor');
  const preferredPath = join(hvigorDir, 'node_modules', '@ohos', 'hvigor', 'package.json');
  const preferred = engines.find(pkg => pkg.path === preferredPath);
  const versions = [...new Set(engines.map(pkg => pkg.version))];
  if (!preferred && versions.length !== 1) {
    throw new Error('Cannot identify a unique SDK @ohos/hvigor engine. Found: ' +
      engines.map(pkg => pkg.version + ' at ' + pkg.path).join(', ') +
      '. Use a complete Huawei Command Line Tools SDK bundle.');
  }
  hvigorVersion = (preferred || engines[0]).version;
  if (!/^\d+\.\d+\.\d+(?:-[\w.-]+)?$/.test(hvigorVersion)) {
    throw new Error('SDK Hvigor package has an invalid version: ' + hvigorVersion);
  }
  if (pluginVersion && pluginVersion !== hvigorVersion) {
    throw new Error('HVIGOR_PLUGIN_VERSION=' + pluginVersion + ' differs from SDK Hvigor ' +
      hvigorVersion + '. Remove the override or use a matching SDK; major-version matching is insufficient.');
  }
  pluginVersion = hvigorVersion;
  console.log('Using SDK @ohos/hvigor@' + hvigorVersion + ' and matching @ohos/hvigor-ohos-plugin@' + pluginVersion);
}

const profile = JSON.parse(readFileSync(join(repo, 'apps', platform, 'build-profile.json5'), 'utf8'));
const product = profile.app.products[0];
if (process.env.HAP_COMPILE_SDK_VERSION) product.compileSdkVersion = platform === 'openharmony'
  ? Number(process.env.HAP_COMPILE_SDK_VERSION) : process.env.HAP_COMPILE_SDK_VERSION;
const staging = mkdtempSync(join(tmpdir(), `health2609-${platform}-`));
const buildEnv = { ...process.env };
function run(executable, params) {
  const result = spawnSync(executable, params, {
    cwd: staging, env: buildEnv, stdio: 'inherit', shell: process.platform === 'win32'
  });
  if (result.error) throw new Error(`Cannot execute ${executable}: ${result.error.message}. Install Huawei Command Line Tools / the OpenHarmony SDK first.`);
  if (result.status !== 0) throw new Error(`${executable} exited ${result.status}`);
}
function files(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap(entry => {
    const path = join(dir, entry.name);
    return entry.isDirectory() ? files(path) : [path];
  });
}
try {
  cpSync(join(repo, 'apps', platform), staging, { recursive: true,
    filter: path => !/[\\/](build|node_modules|\.hvigor|oh_modules)([\\/]|$)/.test(path) && !path.endsWith('local.properties')
  });
  if (tools) {
    const sdk = join(tools, 'sdk');
    if (!existsSync(sdk)) throw new Error('Command Line Tools SDK directory missing');
    if (!buildEnv.DEVECO_SDK_HOME) buildEnv.DEVECO_SDK_HOME = sdk;
    if (platform === 'harmony') {
      writeFileSync(join(staging, 'local.properties'), 'sdk.dir=' + buildEnv.DEVECO_SDK_HOME.replaceAll('\\', '/') + '\n');
    } else if (!buildEnv.OHOS_SDK_HOME) {
      // The Huawei bundle carries its embedded OpenHarmony components flat
      // under sdk/default/openharmony. The standalone OH SDK manager instead
      // expects an API-indexed root: <sdk>/12/{ets,js,native,toolchains,...}.
      // Project it without modifying or deleting the shared verified SDK cache.
      const components = join(sdk, 'default', 'openharmony');
      const names = ['ets', 'js', 'native', 'toolchains', 'previewer'];
      if (!existsSync(components)) throw new Error('Embedded OpenHarmony SDK missing: ' + components);
      const metadataFile = join(components, 'ets', 'oh-uni-package.json');
      if (!existsSync(metadataFile)) throw new Error('OpenHarmony ets SDK component metadata missing: ' + metadataFile);
      const metadata = JSON.parse(readFileSync(metadataFile, 'utf8'));
      const api = Number(metadata.apiVersion ?? metadata.data?.apiVersion);
      if (!Number.isSafeInteger(api) || api <= 0) throw new Error('Cannot determine embedded OpenHarmony SDK API version');
      if (Number(product.compileSdkVersion) !== api) throw new Error('OpenHarmony compile API ' + product.compileSdkVersion + ' does not match embedded SDK API ' + api);
      const sdkRoot = join(staging, '.sdk');
      const apiRoot = join(sdkRoot, String(api));
      mkdirSync(apiRoot, { recursive: true });
      for (const name of names) {
        const source = join(components, name);
        if (!existsSync(source)) {
          if (name === 'previewer' || name === 'native') continue;
          throw new Error('OpenHarmony SDK component missing: ' + source);
        }
        symlinkSync(source, join(apiRoot, name), process.platform === 'win32' ? 'junction' : 'dir');
      }
      buildEnv.OHOS_SDK_HOME = sdkRoot;
      writeFileSync(join(staging, 'local.properties'), 'sdk.dir=' + sdkRoot.replaceAll('\\', '/') + '\n');
      console.log('Projected embedded OpenHarmony API ' + api + ' SDK into an isolated API-indexed root');
    }
  }

  if (signed) {
    const required = ['HAP_CERT_PATH','HAP_PROFILE_PATH','HAP_KEYSTORE_PATH','HAP_KEY_ALIAS','HAP_KEY_PASSWORD','HAP_STORE_PASSWORD'];
    for (const name of required) if (!process.env[name]) throw new Error(`Signed device package requires ${name}`);
    for (const name of required.slice(0,3)) if (!existsSync(process.env[name])) throw new Error(`${name} file missing`);
    profile.app.signingConfigs = [{ name: 'device', type: platform === 'harmony' ? 'HarmonyOS' : 'OpenHarmony', material: {
      certpath: resolve(process.env.HAP_CERT_PATH), profile: resolve(process.env.HAP_PROFILE_PATH),
      storeFile: resolve(process.env.HAP_KEYSTORE_PATH), keyAlias: process.env.HAP_KEY_ALIAS,
      keyPassword: process.env.HAP_KEY_PASSWORD, storePassword: process.env.HAP_STORE_PASSWORD,
      signAlg: 'SHA256withECDSA'
    }}];
    product.signingConfig = 'device';
  }
  writeFileSync(join(staging, 'build-profile.json5'), JSON.stringify(profile, null, 2));
  if (pluginVersion) {
    const path = join(staging, 'hvigor', 'hvigor-config.json5');
    const config = JSON.parse(readFileSync(path, 'utf8'));
    config.dependencies['@ohos/hvigor-ohos-plugin'] = pluginVersion;
    writeFileSync(path, JSON.stringify(config, null, 2));
  }
  const appFile = join(staging, 'AppScope', 'app.json5');
  const app = JSON.parse(readFileSync(appFile, 'utf8'));
  const version = option('--version-name', app.app.versionName);
  const versionCode = Number(option('--version-code', app.app.versionCode));
  if (!Number.isSafeInteger(versionCode) || versionCode <= 0) throw new Error('Invalid version code');
  app.app.versionName = version;
  app.app.versionCode = versionCode;
  writeFileSync(appFile, JSON.stringify(app, null, 2));
  run(ohpm, ['install']);
  run(hvigor, ['--mode', 'module', '-p', 'product=default', '-p', 'module=entry@default', '-p', 'buildMode=release', 'assembleHap', '--no-daemon']);
  const outputDir = join(staging, 'entry', 'build', 'default', 'outputs', 'default');
  if (!existsSync(outputDir)) throw new Error('Hvigor did not generate the HAP output directory');
  const candidates = files(outputDir).filter(file => file.endsWith(signed ? '-signed.hap' : '-unsigned.hap'));
  if (candidates.length !== 1) throw new Error(`Expected one ${signed ? 'signed' : 'unsigned'} HAP, found ${candidates.length}`);
  mkdirSync(out, { recursive: true });
  const filename = `health2609-${platform}-${version}-${signed ? 'signed' : 'unsigned'}.hap`;
  const target = join(out, filename);
  cpSync(candidates[0], target);
  writeFileSync(target + '.sha256', createHash('sha256').update(readFileSync(target)).digest('hex') + '  ' + filename + '\n');
  writeFileSync(join(out, 'build-info.json'), JSON.stringify({ platform, version, versionCode, signed,
    bundleName: app.app.bundleName, compileSdkVersion: product.compileSdkVersion,
    compatibleSdkVersion: product.compatibleSdkVersion, hvigorVersion, pluginVersion, installableOnCommercialHarmony: platform === 'harmony' && signed,
    signingNote: signed ? 'Profile must authorize the target device UDID and match the bundle name.' : 'Requires device-authorized signing before installation.'
  }, null, 2));
  console.log(`Packaged ${target}`);
} finally {
  // Generated signing configuration contains passwords; always discard staging.
  rmSync(staging, { recursive: true, force: true });
}
