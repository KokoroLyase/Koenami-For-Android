import {connect} from './cdp-client.mjs';
const client=await connect();
try { await client.tap(process.argv[2]); console.log('Tapped requested control'); } finally {client.close();}
