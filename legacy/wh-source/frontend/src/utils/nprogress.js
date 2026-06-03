import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

NProgress.configure({
    showSpinner: false,
    minimum: 0.1,
    speed: 200
})

export default NProgress
