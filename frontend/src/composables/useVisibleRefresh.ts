import {onMounted,onUnmounted} from 'vue'
/** Refresh active read views after edits in other sessions, without overlapping requests. */
export function useVisibleRefresh(refresh:()=>Promise<unknown>,enabled:()=>boolean=()=>true) {
 let timer:ReturnType<typeof setInterval>|undefined,busy=false
 async function run(){if(document.visibilityState!=='visible'||!enabled()||busy)return;busy=true;try{await refresh()}finally{busy=false}}
 onMounted(()=>{timer=setInterval(run,30000);window.addEventListener('focus',run)})
 onUnmounted(()=>{clearInterval(timer);window.removeEventListener('focus',run)})
}
