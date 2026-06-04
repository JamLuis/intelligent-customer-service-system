import { resolve } from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'
import { viteStaticCopy } from 'vite-plugin-static-copy'

export default defineConfig({
  plugins: [
    react(),
    viteStaticCopy({
      targets: [
        {
          src: 'node_modules/cesium/Build/Cesium/Workers/**/*',
          dest: 'cesium',
          rename: { stripBase: 4 },
        },
        {
          src: 'node_modules/cesium/Build/Cesium/Assets/**/*',
          dest: 'cesium',
          rename: { stripBase: 4 },
        },
        {
          src: 'node_modules/cesium/Build/Cesium/ThirdParty/**/*',
          dest: 'cesium',
          rename: { stripBase: 4 },
        },
        {
          src: 'node_modules/cesium/Build/Cesium/Widgets/**/*',
          dest: 'cesium',
          rename: { stripBase: 4 },
        },
      ],
    }),
  ],
  build: {
    rollupOptions: {
      input: {
        index: resolve(__dirname, 'index.html'),
        groupSafety: resolve(__dirname, 'group-safety.html'),
        dispatchCenter: resolve(__dirname, 'dispatch-center.html'),
        safetySupervision: resolve(__dirname, 'safety-supervision.html'),
        faultDiagnosis: resolve(__dirname, 'fault-diagnosis.html'),
        fieldAssistant: resolve(__dirname, 'field-assistant.html'),
        digitalTwin: resolve(__dirname, 'digital-twin.html'),
      },
    },
  },
})
